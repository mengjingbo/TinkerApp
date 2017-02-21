#### [Tinker项目](https://github.com/Tencent/tinker)(点击进入)
>Tinker是微信官方的Android热补丁解决方案，它支持动态下发代码。.so库以及资源库，让应用能够在不需要重复安装的情况下实现更新，当然也可以使用Tinker来更新你的插件。

![Tinker原理图](http://upload-images.jianshu.io/upload_images/1503443-ecae5c93fd133687.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

在接入Tinker之前我们先对Tinker的结构了解一下
##### Tinker主要包括一下几个部分：
>1.gradle编译插件：tinker-patch-gradle-plugin。  
2.核心SDK库：tinker-android-lib。  
3.非gradle编译用户的命令行版本：tinker-patch-cil.jar。  

##### Tinker的已知问题:
>1.Tinker不支持修改AndroidMainfest.xml，Tinker不支持新增四大组件。    
2.由于Google Pay的开发者条款限制，不建议在GP渠道动态更新代码。    
3.在Android N上，补丁对应用启动时有轻微的影响。    
4.不支持部分三星android-21机型，加载补丁时会主动抛出“TinkerRuntimeException:checkDexInstall failed”异常。    
5.由于各个厂商加固实现并不一致，在1.7.6以后的版本，Tinker不在支持加固的动态更新。    
6.对于资源替换，不支持修改remoteView，例如transition动画，notification icon以及桌面图标。    

Tinker的修复方案跟Hotfix的修复方案大同小异，都是在两个apk包上作比较然后生成patch。下面对Tinker进行接入。
##### 在工程目录下的build.gradle中添加依赖库
```Java
    buildscript {
        repositories {
            jcenter()
        }
        dependencies {
            classpath 'com.android.tools.build:gradle:2.2.3'
            // 编译插件tinker-patch-gradle-plugin
            classpath 'com.tencent.tinker:tinker-patch-gradle-plugin:1.7.7'
        }
    }
    allprojects {
        repositories {
            jcenter()
        }
    }
    task clean(type: Delete) {
        delete rootProject.buildDir
    }
```
##### 在工程app目录下的build.gradle中添加依赖库
```Java
    dependencies {
        compile fileTree(dir: 'libs', include: ['*.jar'])
        ......
        //可选，用于生成application类
        provided('com.tencent.tinker:tinker-android-anno:1.7.7')
        //tinker的核心库
        compile('com.tencent.tinker:tinker-android-lib:1.7.7')
    }
```
##### API引入
一般我们都是在Application中onCreate()中做初始化和加载patch，不过Tinker推荐如下写法。因为程序启动时会加载默认的Application类，这导致我们补丁包是无法对它做修改了。为了规避这个问题Tinker通过代码框架的方式来避免，这也是为了尽量少的去反射，提升框架的兼容性。
```Java
    @DefaultLifeCycle(
            application = ".AppContext", flags = ShareConstants.TINKER_ENABLE_ALL
    )
    public class AppContextLike extends ApplicationLike {


        public AppContextLike(Application application, int tinkerFlags, boolean tinkerLoadVerifyFlag, long applicationStartElapsedTime, long applicationStartMillisTime, Intent tinkerResultIntent) {
            super(application, tinkerFlags, tinkerLoadVerifyFlag, applicationStartElapsedTime, applicationStartMillisTime, tinkerResultIntent);
        }

        @Override
        public void onCreate() {
            super.onCreate();
            TinkerInstaller.install(this);
        }
    }
```
代码中AppContextLike继承了ApplicationLike，而ApplicationLike并非集成Application，而是类似于Application的一个类。Tinker建议编写一个ApplicationLike的子类，可以当做Application使用，注意顶部的注解：@DefaultLifeCycle，其application属性，会在编译期生成一个**.AppContext**类。所以我们在AndroidManifest.xml中的application标签下这样写：
```javaScript
    <application
        android:name=".AppContext"
        ......
    </application>
```
写完后会报红，此时只需要Build下即可解决报红。Application配置就到此结束。接下来生成patch文件。因为patch文件是写入到SDCrad的，所以我们需要在AndroidManifest中添加如下权限（**注：** ***6.0及已上系统请动态设置权限或者手动在设置中为项目设置***）：
```javaScript
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/>
```
Tinker需要在AndroidManifest.xml中指定TINKER_ID
```javaScript
    <meta-data
        android:name="TINKER_ID"
        android:value="tinker_id_100" />
```
##### Patch生成
patch生成官方提供了两种接入方式：
>1.gradle编译的方式。     
2.基于命令行的方式。       

**1.gradle编译生成patch**
微信Tinker的gradle配置也很简单，先来浏览一下[Tinker接入指南，点击进入查看](https://github.com/Tencent/tinker/wiki/Tinker-%E6%8E%A5%E5%85%A5%E6%8C%87%E5%8D%97)，对使用gradle配置的参数了解一下，接下来附上一个相对比较完整的gradle配置。
```java
apply plugin: 'com.android.application'

android {

    compileSdkVersion 25
    buildToolsVersion "25.0.2"

    compileOptions {
        sourceCompatibility getJavaVersion()
        targetCompatibility getJavaVersion()
    }

    // Tinker推荐模式
    dexOptions {
        jumboMode = true
    }

    // 关闭aapt对png优化
    aaptOptions {
        cruncherEnabled false
    }

    signingConfigs {
        try {
            config {
                keyAlias 'testres'
                keyPassword 'testres'
                storeFile file('./keystore/release.keystore')
                storePassword 'testres'
            }
        } catch (ex) {
            throw new InvalidUserDataException(ex.toString())
        }
    }

    defaultConfig {
        applicationId "com.tinker.app"
        minSdkVersion 15
        targetSdkVersion 25
        versionCode 1
        versionName "1.0.0"
        // 使用multiDex库
        multiDexEnabled true
        // 设置签名
        signingConfig signingConfigs.config
        manifestPlaceholders = [TINKER_ID: "${getTinkerIdValue()}"]
        buildConfigField "String", "MESSAGE", "\"I am the base apk\""
        buildConfigField "String", "CLIENTVERSION", "\"${getTinkerIdValue()}\""
        buildConfigField "String", "PLATFORM",  "\"all\""
    }

    buildTypes {
        release {
            minifyEnabled false
            signingConfig signingConfigs.config
            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro'
        }
        debug {
            minifyEnabled false
            signingConfig signingConfigs.config
        }
    }

    sourceSets {
        main {
            jniLibs.srcDirs = ['libs']
        }
    }
}

dependencies {
    compile fileTree(include: ['*.jar'], dir: 'libs')
    compile 'com.android.support:appcompat-v7:25.1.0'
    // 依赖multiDex库
    compile 'com.android.support:multidex:1.0.1'
    //可选，用于生成application类
    provided ("com.tencent.tinker:tinker-android-anno:${TINKER_VERSION}"){changing = true}
    //Tinker的核心库
    compile ("com.tencent.tinker:tinker-android-lib:${TINKER_VERSION}"){changing = true}
}

// 指定JDK版本
def getJavaVersion() {
    return JavaVersion.VERSION_1_7
}

def bakPath = file("${buildDir}/bakApk/")

/**
 * ext相关配置
 */
ext {
    tinkerEnabled = true
    // 基础版本apk
    tinkerOldApkPath = "${bakPath}/app-debug-20170217-18-51-30.apk"
    // 未开启混淆的话mapping可以忽略，如果开启混淆mapping要保持一致。 
    tinkerApplyMappingPath = "${bakPath}/"
    // 与基础版本一起生成的R.text文件
    tinkerApplyResourcePath = "${bakPath}/app-debug-20170217-18-51-30-R.txt"
    // 只用于构建所有的Build,如果不是,此处可忽略。
    tinkerBuildFlavorDirectory = "${bakPath}/"
}

// 基础APK的位置
def getOldApkPath() {
    return hasProperty("OLD_APK") ? OLD_APK : ext.tinkerOldApkPath
}

// Mapping的位置
def getApplyMappingPath() {
    return hasProperty("APPLY_MAPPING") ? APPLY_MAPPING : ext.tinkerApplyMappingPath
}

// ResourceMapping的位置
def getApplyResourceMappingPath() {
    return hasProperty("APPLY_RESOURCE") ? APPLY_RESOURCE : ext.tinkerApplyResourcePath
}

// 用来获取TinkerId(当前版本号就是TinkerId)
def getTinkerIdValue() {
    return android.defaultConfig.versionName
}

def buildWithTinker() {
    return hasProperty("TINKER_ENABLE") ? TINKER_ENABLE : ext.tinkerEnabled
}

def getTinkerBuildFlavorDirectory() {
    return ext.tinkerBuildFlavorDirectory
}

if (buildWithTinker()) {
    // Tinker插件
    apply plugin: 'com.tencent.tinker.patch'
    /**
     * 全局信息相关配置
     */
    tinkerPatch {
        // 基准apk包的路径，必须输入，否则会报错。
        oldApk = getOldApkPath()
        /**
         * 如果出现以下的情况，并且ignoreWarning为false，我们将中断编译。
         * 因为这些情况可能会导致编译出来的patch包带来风险：
         * case 1: minSdkVersion小于14，但是dexMode的值为"raw";
         * case 2: 新编译的安装包出现新增的四大组件(Activity, BroadcastReceiver...)；
         * case 3: 定义在dex.loader用于加载补丁的类不在main dex中;
         * case 4:  定义在dex.loader用于加载补丁的类出现修改；
         * case 5: resources.arsc改变，但没有使用applyResourceMapping编译。
         */
        ignoreWarning = false

        /**
         * 运行过程中需要验证基准apk包与补丁包的签名是否一致，是否需要签名。
         */
        useSign = true

        /**
         * optional，default 'true'
         * whether use tinker to build
         */
        tinkerEnable = buildWithTinker()

        /**
         * 编译相关的配置项
         */
        buildConfig {
            /**
             * 可选参数；在编译新的apk时候，我们希望通过保持旧apk的proguard混淆方式，从而减少补丁包的大小。
             * 这个只是推荐设置，不设置applyMapping也不会影响任何的assemble编译。
             */
            applyMapping = getApplyMappingPath()
            /**
             * 可选参数；在编译新的apk时候，我们希望通过旧apk的R.txt文件保持ResId的分配。
             * 这样不仅可以减少补丁包的大小，同时也避免由于ResId改变导致remote view异常。
             */
            applyResourceMapping = getApplyResourceMappingPath()

            /**
             * 在运行过程中，我们需要验证基准apk包的tinkerId是否等于补丁包的tinkerId。
             * 这个是决定补丁包能运行在哪些基准包上面，一般来说我们可以使用git版本号、versionName等等。
             */
            tinkerId = getTinkerIdValue()

            /**
             * 如果我们有多个dex,编译补丁时可能会由于类的移动导致变更增多。
             * 若打开keepDexApply模式，补丁包将根据基准包的类分布来编译。
             */
            keepDexApply = false
        }
        /**
         * dex相关的配置项
         */
        dex {
            /**
             * 只能是'raw'或者'jar'。
             * 对于'raw'模式，我们将会保持输入dex的格式。
             * 对于'jar'模式，我们将会把输入dex重新压缩封装到jar。
             * 如果你的minSdkVersion小于14，你必须选择‘jar’模式，而且它更省存储空间，但是验证md5时比'raw'模式耗时。
             * 默认我们并不会去校验md5,一般情况下选择jar模式即可。
             */
            dexMode = "jar"

            /**
             * 需要处理dex路径，支持*、?通配符，必须使用'/'分割。路径是相对安装包的，例如assets/...
             */
            pattern = ["classes*.dex",
                       "assets/secondary-dex-?.jar"]
            /**
             * 这一项非常重要，它定义了哪些类在加载补丁包的时候会用到。
             * 这些类是通过Tinker无法修改的类，也是一定要放在main dex的类。
             * 这里需要定义的类有：
             * 1. 你自己定义的Application类；
             * 2. Tinker库中用于加载补丁包的部分类，即com.tencent.tinker.loader.*；
             * 3. 如果你自定义了TinkerLoader，需要将它以及它引用的所有类也加入loader中；
             * 4. 其他一些你不希望被更改的类，例如Sample中的BaseBuildInfo类。
             *    这里需要注意的是，这些类的直接引用类也需要加入到loader中。
             *    或者你需要将这个类变成非preverify。
             * 5. 使用1.7.6版本之后版本，参数1、2会自动填写。
             *
             */
            loader = [
                    // Tinker库中用于加载补丁包的部分类
                    "com.tencent.tinker.loader.*",
                    // 自己定义的Application类；
                    "com.tinker.app.AppContext",
                    //use sample, let BaseBuildInfo unchangeable with tinker
                    "tinker.sample.android.app.BaseBuildInfo"
            ]
        }
        /**
         * lib相关的配置项
         */
        lib {
            /**
             * 需要处理lib路径，支持*、?通配符，必须使用'/'分割。
             * 与dex.pattern一致, 路径是相对安装包的，例如assets/...
             */
            pattern = ["lib/*/*.so"]
        }
        /**
         * res相关的配置项
         */
        res {
            /**
             * 需要处理res路径，支持*、?通配符，必须使用'/'分割。
             * 与dex.pattern一致, 路径是相对安装包的，例如assets/...，
             * 务必注意的是，只有满足pattern的资源才会放到合成后的资源包。
             */
            pattern = ["res/*", "assets/*", "resources.arsc", "AndroidManifest.xml"]

            /**
             * 支持*、?通配符，必须使用'/'分割。若满足ignoreChange的pattern，在编译时会忽略该文件的新增、删除与修改。
             * 最极端的情况，ignoreChange与上面的pattern一致，即会完全忽略所有资源的修改。
             */
            ignoreChange = ["assets/sample_meta.txt"]

            /**
             * 对于修改的资源，如果大于largeModSize，我们将使用bsdiff算法。
             * 这可以降低补丁包的大小，但是会增加合成时的复杂度。默认大小为100kb
             */
            largeModSize = 100
        }
        /**
         * 用于生成补丁包中的'package_meta.txt'文件
         */
        packageConfig {
            /**
             * configField("key", "value"), 默认我们自动从基准安装包与新安装包的Manifest中读取tinkerId,并自动写入configField。
             * 在这里，你可以定义其他的信息，在运行时可以通过TinkerLoadResult.getPackageConfigByName得到相应的数值。
             * 但是建议直接通过修改代码来实现，例如BuildConfig。
             */
            configField("patchMessage", "tinker is sample to use")
            /**
             * just a sample case, you can use such as sdkVersion, brand, channel...
             * you can parse it in the SamplePatchListener.
             * Then you can use patch conditional!
             */
            configField("platform", "all")
            /**
             * 配置patch补丁版本
             */
            configField("patchVersion", "1.0.0")
        }
        /**
         * 7zip路径配置项，执行前提是useSign为true
         */
        sevenZip {
            /**
             * 例如"com.tencent.mm:SevenZip:1.1.10"，将自动根据机器属性获得对应的7za运行文件，推荐使用。
             */
            zipArtifact = "com.tencent.mm:SevenZip:1.1.10"
            /**
             * 系统中的7za路径，例如"/usr/local/bin/7za"。path设置会覆盖zipArtifact，若都不设置，将直接使用7za去尝试。
             */
            // path = "/usr/local/bin/7za"
        }
    }

    List<String> flavors = new ArrayList<>();
    project.android.productFlavors.each { flavor ->
        flavors.add(flavor.name)
    }
    boolean hasFlavors = flavors.size() > 0
    /**
     * bak apk and mapping
     */
    android.applicationVariants.all { variant ->
        /**
         * task type, you want to bak
         */
        def taskName = variant.name
        def date = new Date().format("yyyyMMdd-HH-mm-ss")

        tasks.all {
            if ("assemble${taskName.capitalize()}".equalsIgnoreCase(it.name)) {

                it.doLast {
                    copy {
                        def fileNamePrefix = "${project.name}-${variant.baseName}"
                        def newFileNamePrefix = hasFlavors ? "${fileNamePrefix}" : "${fileNamePrefix}-${date}"

                        def destPath = hasFlavors ? file("${bakPath}/${project.name}-${date}/${variant.flavorName}") : bakPath
                        from variant.outputs.outputFile
                        into destPath
                        rename { String fileName ->
                            fileName.replace("${fileNamePrefix}.apk", "${newFileNamePrefix}.apk")
                        }

                        from "${buildDir}/outputs/mapping/${variant.dirName}/mapping.txt"
                        into destPath
                        rename { String fileName ->
                            fileName.replace("mapping.txt", "${newFileNamePrefix}-mapping.txt")
                        }

                        from "${buildDir}/intermediates/symbols/${variant.dirName}/R.txt"
                        into destPath
                        rename { String fileName ->
                            fileName.replace("R.txt", "${newFileNamePrefix}-R.txt")
                        }
                    }
                }
            }
        }
    }
    project.afterEvaluate {
        //sample use for build all flavor for one time
        if (hasFlavors) {
            task(tinkerPatchAllFlavorRelease) {
                group = 'tinker'
                def originOldPath = getTinkerBuildFlavorDirectory()
                for (String flavor : flavors) {
                    def tinkerTask = tasks.getByName("tinkerPatch${flavor.capitalize()}Release")
                    dependsOn tinkerTask
                    def preAssembleTask = tasks.getByName("process${flavor.capitalize()}ReleaseManifest")
                    preAssembleTask.doFirst {
                        String flavorName = preAssembleTask.name.substring(7, 8).toLowerCase() + preAssembleTask.name.substring(8, preAssembleTask.name.length() - 15)
                        project.tinkerPatch.oldApk = "${originOldPath}/${flavorName}/${project.name}-${flavorName}-release.apk"
                        project.tinkerPatch.buildConfig.applyMapping = "${originOldPath}/${flavorName}/${project.name}-${flavorName}-release-mapping.txt"
                        project.tinkerPatch.buildConfig.applyResourceMapping = "${originOldPath}/${flavorName}/${project.name}-${flavorName}-release-R.txt"
                    }
                }
            }

            task(tinkerPatchAllFlavorDebug) {
                group = 'tinker'
                def originOldPath = getTinkerBuildFlavorDirectory()
                for (String flavor : flavors) {
                    def tinkerTask = tasks.getByName("tinkerPatch${flavor.capitalize()}Debug")
                    dependsOn tinkerTask
                    def preAssembleTask = tasks.getByName("process${flavor.capitalize()}DebugManifest")
                    preAssembleTask.doFirst {
                        String flavorName = preAssembleTask.name.substring(7, 8).toLowerCase() + preAssembleTask.name.substring(8, preAssembleTask.name.length() - 13)
                        project.tinkerPatch.oldApk = "${originOldPath}/${flavorName}/${project.name}-${flavorName}-debug.apk"
                        project.tinkerPatch.buildConfig.applyMapping = "${originOldPath}/${flavorName}/${project.name}-${flavorName}-debug-mapping.txt"
                        project.tinkerPatch.buildConfig.applyResourceMapping = "${originOldPath}/${flavorName}/${project.name}-${flavorName}-debug-R.txt"
                    }
                }
            }
        }
    }
}
```
gradle配置就到此结束了，要注意的地方有以下几点：
>1.ext相关配置，示例中有完整描述。    
2.Tinker插件``` apply plugin: 'com.tencent.tinker.patch'```    
3.全局信息相关配置tinkerPatch     

配置完这些东西以后就可以调用tinkerPatch命令生成patch补丁文件。tinkerPatch有Debug和Release两种模式，因为是案例，所以就使用tinkerPatchDebug命令。
![tinkerPatch命令](http://upload-images.jianshu.io/upload_images/1503443-e811b53371c06da6.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)
![调用tinkerPatchDebug命令后生成7patch文件](http://upload-images.jianshu.io/upload_images/1503443-d18f9a94f5b7aa05.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)
注意：调用tinkerPatchDebug命令之前需要修改ext相关配置，ext相关配置已基准apk包为准。
![ext相关配置](http://upload-images.jianshu.io/upload_images/1503443-b363552e113ba924.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

![7zip文件](http://upload-images.jianshu.io/upload_images/1503443-15ea8165976d1f61.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

![加载补丁前](http://upload-images.jianshu.io/upload_images/1503443-bd4ae5c43d900351.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)
![加载补丁后会提示“加载patch成功，请重启进程”](http://upload-images.jianshu.io/upload_images/1503443-ba4929e5047f2dcd.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)
![重启进程后，就会显示出该改好的内容](http://upload-images.jianshu.io/upload_images/1503443-b416f8637f101d48.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)
到此微信Tinker热修复gradle配置结束。
