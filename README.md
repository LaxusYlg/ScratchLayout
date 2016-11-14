# ScratchLayout
A scratch view implemented with ViewGroup which make view content more flexible.
![](/screenshot/screenrecord.gif)

Usage
-----

### Dependencies
With Gradle

```groovy
    dependencies {
        compile 'com.laxus.scratchlayout:scratchlayout:1.0.0'
    }

```



### Xml

```xml
<com.laxus.android.scratchlayout.ScratchLayout
                        android:id="@+id/sl_pokemon_image"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_gravity="center"
                        app:mask="@drawable/pokeball"
                        app:maskMode="repeat"[enlarge|fit|repeat]
                        app:revealPercent="60"
                        app:strokeWidth="20dp"
                        app:autoReveal="true">

                        ....

</com.laxus.android.scratchlayout.ScratchLayout>

```

### Java

```java
  mScratchLayout.addOnRevealListener(new OnRevealListener {
    @Override
    public void onRevealed(ScratchLayout scratch) {
       scrach.reset();
    }
  });

```
