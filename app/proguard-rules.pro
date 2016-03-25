-keepattributes SourceFile,LineNumberTable
-keep class android.support.v7.internal.view.menu.** {*;}
-keep class android.support.v7.widget.SearchView {*;}
-keep class android.support.v7.widget.ActionMenuPresenter {*;}
-keep class android.support.v7.widget.ActionMenuView {*;}
-keep class android.support.v7.widget.Toolbar {*;}
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class com.wnafee.vector.** { *; }
-keep public enum com.bumptech.glide.load.resource.bitmap.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}
-keepclassmembers class com.github.clans.fab.FloatingActionMenu {
    private com.github.clans.fab.FloatingActionButton mMenuButton;
}
-dontwarn
-ignorewarnings