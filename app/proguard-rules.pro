# Keep the Android application entry points and VPN service.
-keep class com.labuda.app.MainActivity { *; }
-keep class com.labuda.app.QrScannerActivity { *; }
-keep class com.labuda.app.LabudaVpnService { *; }

# libv2ray uses reflection/Go bindings. Keep its public bridge surface.
-keep class libv2ray.** { *; }
-keep class go.** { *; }

# Do not emit source/debug metadata into the release artifact.
-renamesourcefileattribute SourceFile
-keepattributes Exceptions,InnerClasses,Signature,RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations
