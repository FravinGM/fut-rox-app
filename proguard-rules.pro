# Mantém só o necessário; o resto é removido/ofuscado pelo R8.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Remove chamadas de log em release (nada de dados vazando no logcat).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}
