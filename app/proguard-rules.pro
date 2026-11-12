# Pleos Vehicle SDK — AAR proguard.txt 와 동일 규칙을 앱에도 유지
-keep class ai.pleos.playground.vehicle.api.implement.* { *; }
-keepattributes *Annotation*
-keepattributes Signature,InnerClasses,EnclosingMethod,LineNumberTable,SourceFile

# kotlinx.serialization
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keepclassmembers class com.cardoctor.advisor.** {
    *** Companion;
}
-keepclasseswithmembers class com.cardoctor.advisor.** {
    kotlinx.serialization.KSerializer serializer(...);
}
