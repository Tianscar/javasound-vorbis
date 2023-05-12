# Java Implementation of Vorbis
This is a fork of [JVorbis](https://github.com/consulo/jvorbis), backported to Java 8.

This library is a Java port of the Ogg Vorbis codec from xiph.org.

Original port: by http://dmilvdv.narod.ru/Apps/oggvorbis.html
Original code: https://www.xiph.org/vorbis/

## Add the library to your project (gradle)
1. Add the Maven Central repository (if not exist) to your build file:
```groovy
repositories {
    ...
    mavenCentral()
}
```

2. Add the dependency:
```groovy
dependencies {
    ...
    implementation 'com.tianscar.javasound:javasound-vorbis:2.0.0'
}
```

## Usage
[Tests and Examples](/src/test/java/com/github/jvorbis/test)  
[Command-line interfaces](/src/test/com/github/jvorbis/cli)

Note you need to download test audios [here](https://github.com/Tianscar/fbodemo1) and put them to /src/test/resources to run the test code properly!

## License
[LGPL-2.0](/LICENSE)
