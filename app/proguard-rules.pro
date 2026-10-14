# PauzFirst release rules.
# Conservative on purpose: the app's own code is kept unrenamed and unchanged,
# so nothing that is looked up by name (manifest components, JSON field names,
# notification and widget receivers) can break. R8 still removes unused library code.
-keep class com.pauzfirst.app.** { *; }
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature, SourceFile, LineNumberTable
-dontwarn javax.annotation.**
