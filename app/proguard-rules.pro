-dontobfuscate

-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep class hev.htproxy.TProxyService { *; }
-keepclasseswithmembers,includedescriptorclasses class hev.htproxy.TProxyService {
    native <methods>;
}
-keep class dev.amirzr.flutter_v2ray_client.v2ray.core.HevTunCore { *; }

-keep class dev.zeptun.Zeptun { *; }
-keepclasseswithmembers,includedescriptorclasses class dev.zeptun.Zeptun {
    native <methods>;
}
-keep class libv2ray.** { *; }
-keep class go.** { *; }

-keep class org.bouncycastle.crypto.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn javax.naming.**

-keepclassmembers class com.zvpn.** {
    *** Companion;
}
-keep @kotlinx.serialization.Serializable class com.zvpn.** { *; }

-allowaccessmodification

-keep class ca.psiphon.** { *; }
-keep class psi.** { *; }

-keep class zvpndns.** { *; }

-keep class libbox.** { *; }

-keep class masterdns.** { *; }

-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**

-keep class org.infradead.libopenconnect.** { *; }
-keep class com.zvpn.core.OpenConnectController { *; }
-keep class com.zvpn.core.OpenConnectController$* { *; }

-dontwarn com.google.zxing.**
-keep class com.google.zxing.qrcode.** { *; }
-keep class com.google.zxing.MultiFormatReader { *; }
-keep class com.google.zxing.PlanarYUVLuminanceSource { *; }
-keep class com.google.zxing.RGBLuminanceSource { *; }
-keep class com.google.zxing.common.HybridBinarizer { *; }
