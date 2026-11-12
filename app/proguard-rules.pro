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

# Ktor(OkHttp 엔진)가 slf4j API 를 참조하지만, 안드로이드에서는 바인딩 구현이 없다.
# slf4j 2.x 에는 StaticLoggerBinder 자체가 존재하지 않으므로 경고만 끈다.
-dontwarn org.slf4j.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Ktor 내부 리플렉션
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
