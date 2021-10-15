# Support Android 11

# Audio Widget Overlay View [![Awesome](https://cdn.rawgit.com/sindresorhus/awesome/d7305f38d29fed78fa85652e3a63e154dd8e8829/media/badge.svg)](https://github.com/sindresorhus/awesome) <img src="https://www.cleveroad.com/public/comercial/label-android.svg" height="19"> <a href="https://www.cleveroad.com/?utm_source=github&utm_medium=label&utm_campaign=contacts"><img src="https://www.cleveroad.com/public/comercial/label-cleveroad.svg" height="19"></a>
![Header image](/images/header_.jpg)

## Meet the Audio Widget Overlay View by Cleveroad

You know, guys, we have become a bit obsessed with music recently. See yourself by checking our previous work on the Audio Visualization View library. Since we wanted to create a companion to our equalizer and add to the collection of Android widgets, the idea to develop a nice and convenient audio widget was born almost immediately. So if you have already developed a music player and you feel that you want to make it even better, then you’re welcome to use our library and enjoy the results. 

![Demo image](/images/demo.gif)

###### Also you can watch the animation of the <strong><a target="_blank" href="https://youtu.be/4qehnkTR8z8?list=PLi-FH7__aeiydOwY_1q5I8P2EUSseqUCj">Audio Widget Overlay on YouTube</a></strong> in HD quality.

Our invention can facilitate the user’s interaction with a music player and nicely decorate his device screen. Moreover, it’s really easy to integrate! Read our <strong><a href="https://www.cleveroad.com/blog/case-study-audio-widget-overlay-view-by-cleveroad">Case Study: Audio Widget Overlay View by Cleveroad</a></strong> to ensure that and find more of useful information.


[![Article image](/images/article.jpg)](https://www.cleveroad.com/blog/case-study-audio-widget-overlay-view-by-cleveroad)
<br/><br/>
[![Awesome](/images/logo-footer.png)](https://www.cleveroad.com/?utm_source=github&utm_medium=label&utm_campaign=contacts)
<br/>
## Setup and usage
To use Audio Widget Overlay first add dependency to your project:
 
```groovy
dependencies {
    implementation 'com.cleveroad:audiowidget:1.0.2'
}
```
This library will add two new permissions to your manifest:
```XML
<!-- used for drawing widget. This permission must be granted before calling AudioWidget.show(). -->
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW"/>

<!-- used for notifing user that he is about to remove widget when he drags it on remove widget icon. -->
<!-- This permission granted by default on Android 6.0+ devices. -->
<uses-permission android:name="android.permission.VIBRATE"/>
```
 
Then you can create new instance of widget using builder:
```JAVA
AudioWidget audioWidget = new AudioWidget.Builder(context)
        .lightColor(...)
        .darkColor(...)
        .expandWidgetColor(...)
        .progressColor(...)
        .progressStrokeWidth(...)
        .crossColor(...)
        .crossOverlappedColor(...)
        .crossStrokeWidth(...)
        .buttonPadding(...)
        .bubblesMinSize(...)
        .bubblesMaxSize(...)
        .shadowColor(...)
        .shadowRadius(...)
        .shadowDx(...)
        .shadowDy(...)
        .playDrawable(...)
        .pauseDrawable(...)
        .playlistDrawable(...)
        .prevTrackDrawale(...)
        .nextTrackDrawable(...)
        .defaultAlbumDrawable(...)
        .edgeOffsetXCollapsed(...)
        .edgeOffsetYCollapsed(...)
        .edgeOffsetXExpanded(...)
        .edgeOffsetYExpanded(...)
        .build();
```

Or you can use default configuration. Just call:
```JAVA
AudioWidget audioWidget = new AudioWidget.Builder(context).build();
```

Then you can use audio widget's controller to listen for events:

```JAVA
// media buttons' click listener
audioWidget.controller().onControlsClickListener(new AudioWidget.OnControlsClickListener() {
    @Override
    public boolean onPlaylistClicked() {
        // playlist icon clicked
        // return false to collapse widget, true to stay in expanded state
    }

    @Override
    public void onPreviousClicked() {
        // previous track button clicked
    }

    @Override
    public boolean onPlayPauseClicked() {
        // return true to change playback state of widget and play button click animation (in collapsed state)
        return true;
    }

    @Override
    public void onNextClicked() {
        // next track button clicked
    }

    @Override
    public void onAlbumClicked() {
        // album cover clicked
    }
    
    @Override
    public void onPlaylistLongClicked() {
        // playlist button long clicked
    }
    
    @Override
    public void onPreviousLongClicked() {
        // previous track button long clicked
    }
    
    @Override
    public void onPlayPauseLongClicked() {
        // play/pause button long clicked
    }
    
    @Override
    public void onNextLongClicked() {
        // next track button long clicked
    }

    @Override
    public void onAlbumClicked() {
        // album cover long clicked
    }

    @Override
    public void onAlbumLongClicked() {
        // album cover long clicked
    }
});

// widget's state listener
audioWidget.controller().onWidgetStateChangedListener(new AudioWidget.OnWidgetStateChangedListener() {
    @Override
    public void onWidgetStateChanged(@NonNull AudioWidget.State state) {
        // widget state changed (COLLAPSED, EXPANDED, REMOVED)
    }

    @Override
    public void onWidgetPositionChanged(int cx, int cy) {
        // widget position change. Save coordinates here to reuse them next time AudioWidget.show(int, int) called.
    }
});
```

Using AudioWidget.Controller, you can set track's duration, current position or album cover. Also you can set current playback state using start(), pause() or stop() methods. See **MusicService** class for more info on how to use controller.

To show audio widget on screen call **AudioWidget.show(int, int)** method. To hide it call **AudioWidget.hide()** method. Very simple!
```JAVA
audioWidget.show(100, 100); // coordinates in pixels on screen from top left corner
...
audioWidget.hide();
```

But make sure that your app has permission to draw over another apps in Android 6.0+. You can do it like this (in Activity):
```JAVA
    ...
    
    // somewhere in your code
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
        startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE);
    }
    
    ...
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OVERLAY_PERMISSION_REQ_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                // now you can show audio widget
            }
        }
    }
    
    ...
```

#### Migration from v.0.9.1 to v.0.9.2
* **OnControlsClickListener.onPlaylistClicked** should return `true` to consume the action or `false` to use default behavior (collapse the widget)
* **OnControlsClickListener.onPlayPauseClicked** should return `true` to consume the action or `false` to use default behavior (change play/pause state)

## Changelog
See [changelog history].

<br />
#### Support ####
* * *
If you have any other questions regarding the use of this library, please contact us for support at info@cleveroad.com (email subject: "Audio Widget Overlay View. Support request.") 

<br />
#### License ####
* * *
    The MIT License (MIT)
    
    Copyright (c) 2016 Cleveroad Inc.
    
    Permission is hereby granted, free of charge, to any person obtaining a copy
    of this software and associated documentation files (the "Software"), to deal
    in the Software without restriction, including without limitation the rights
    to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
    copies of the Software, and to permit persons to whom the Software is
    furnished to do so, subject to the following conditions:
    
    The above copyright notice and this permission notice shall be included in all
    copies or substantial portions of the Software.
    
    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
    IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
    FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
    AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
    LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
    OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
    SOFTWARE.

[changelog history]: /CHANGELOG.md
