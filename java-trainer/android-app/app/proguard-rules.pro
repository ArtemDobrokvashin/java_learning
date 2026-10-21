# Правила ProGuard / R8 для сохранения моделей Gson (progress.json и JSON курса)
-keep class io.javada.android.data.** { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod
