# kotlinx.serialization keeps generated serializers via its own consumer rules; keep our models' companions too.
-keepclassmembers @kotlinx.serialization.Serializable class ke.floodguard.app.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
