# ML Kit (barcode scanning) creates its components by reflection through their no-argument
# constructors. R8 full mode strips those, so scanning would fail in release builds only.
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
-keep class com.google.mlkit.**.*Registrar { <init>(); }
