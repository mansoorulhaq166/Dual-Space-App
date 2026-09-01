# Used by minify*AndroidTestWithR8 (testProguardFiles). App proguardFiles are NOT applied
# to the androidTest APK.
#
# errorprone annotations reference JDK annotation-model enums that do not exist on Android.
-dontwarn javax.lang.model.element.Modifier
-dontwarn javax.lang.model.element.**
-dontwarn javax.lang.model.**
-dontwarn com.google.errorprone.annotations.**
