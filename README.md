### DrawJav
Library for integrating Jav into Draw3D. 

This is mostly a threading/resource management library. It manages the realtime decoding, transcoding, and delivery of audio/video packets with user controlled playback.

I could probably separate the playback code from the tiny amount of draw3d code and remove the large number of draw3d/OpenGL dependencies, but don't have any plans to do so. 


### Build:
$ ant


### Runtime:
After build, add everything (\*.jar, \*.dylib, \*.jnilib) in **lib** and **target** folders to your project. All jnilib/dylib files must be kept in the same directory (or you must update the install paths with, eg, "install_name_tool"), and that directory must be added to your "java.library.path" runtime property ("java -Djava.library.path=lib_dir")


### Dependencies:
JAV, Draw3D, and all of their dependencies.

---
Author: Philip DeCamp
