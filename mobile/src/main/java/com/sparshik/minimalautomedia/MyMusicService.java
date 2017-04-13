package com.sparshik.minimalautomedia;

import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.browse.MediaBrowser;
import android.media.browse.MediaBrowser.MediaItem;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.service.media.MediaBrowserService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * This class provides a MediaBrowser through a service. It exposes the media library to a browsing
 * client, through the onGetRoot and onLoadChildren methods. It also creates a MediaSession and
 * exposes it through its MediaSession.Token, which allows the client to create a MediaController
 * that connects to and send control commands to the MediaSession remotely. This is useful for
 * user interfaces that need to interact with your media session, like Android Auto. You can
 * (should) also use the same service from your app's UI, which gives a seamless playback
 * experience to the user.
 * <p>
 * To implement a MediaBrowserService, you need to:
 * <p>
 * <ul>
 * <p>
 * <li> Extend {@link android.service.media.MediaBrowserService}, implementing the media browsing
 * related methods {@link android.service.media.MediaBrowserService#onGetRoot} and
 * {@link android.service.media.MediaBrowserService#onLoadChildren};
 * <li> In onCreate, start a new {@link android.media.session.MediaSession} and notify its parent
 * with the session's token {@link android.service.media.MediaBrowserService#setSessionToken};
 * <p>
 * <li> Set a callback on the
 * {@link android.media.session.MediaSession#setCallback(android.media.session.MediaSession.Callback)}.
 * The callback will receive all the user's actions, like play, pause, etc;
 * <p>
 * <li> Handle all the actual music playing using any method your app prefers (for example,
 * {@link android.media.MediaPlayer})
 * <p>
 * <li> Update playbackState, "now playing" metadata and queue, using MediaSession proper methods
 * {@link android.media.session.MediaSession#setPlaybackState(android.media.session.PlaybackState)}
 * {@link android.media.session.MediaSession#setMetadata(android.media.MediaMetadata)} and
 * {@link android.media.session.MediaSession#setQueue(java.util.List)})
 * <p>
 * <li> Declare and export the service in AndroidManifest with an intent receiver for the action
 * android.media.browse.MediaBrowserService
 * <p>
 * </ul>
 * <p>
 * To make your app compatible with Android Auto, you also need to:
 * <p>
 * <ul>
 * <p>
 * <li> Declare a meta-data tag in AndroidManifest.xml linking to a xml resource
 * with a &lt;automotiveApp&gt; root element. For a media app, this must include
 * an &lt;uses name="media"/&gt; element as a child.
 * For example, in AndroidManifest.xml:
 * &lt;meta-data android:name="com.google.android.gms.car.application"
 * android:resource="@xml/automotive_app_desc"/&gt;
 * And in res/values/automotive_app_desc.xml:
 * &lt;automotiveApp&gt;
 * &lt;uses name="media"/&gt;
 * &lt;/automotiveApp&gt;
 * <p>
 * </ul>
 *
 * @see <a href="README.md">README.md</a> for more details.
 */
public class MyMusicService extends MediaBrowserService {

    private MediaSession mSession;
    private List<MediaMetadata> mMusic;
    private MediaPlayer mMediaPlayer;
    private MediaMetadata mCurrentTrack;

    @Override
    public void onCreate() {
        super.onCreate();

        // Create entries for two songs
        mMusic = new ArrayList<>();
        mMusic.add(new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, "https://www.mcgill.ca/counselling/files/counselling/a_moment_to_reflect_1.mp3")
                .putString(MediaMetadata.METADATA_KEY_TITLE, "Music 1")
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "Artist 1")
                .putLong(MediaMetadata.METADATA_KEY_DURATION, 30000)
                .build());

        mMusic.add(new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, "https://www.mcgill.ca/counselling/files/counselling/ocean_imagery.mp3")
                .putString(MediaMetadata.METADATA_KEY_TITLE, "Music 2")
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "Artist 2")
                .putLong(MediaMetadata.METADATA_KEY_DURATION, 30000)
                .build());

        // Responsible for playing back the music
        mMediaPlayer = new MediaPlayer();

        //Media Session Object
        mSession = new MediaSession(this, "MyMusicService");
        //Callbacks to handle events from the user (play, pause, search)
        mSession.setCallback(new MediaSessionCallback());
        mSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mSession.setActive(true);
        setSessionToken(mSession.getSessionToken());

    }

    //Helper method to construct the PlaybackSate we need
    private PlaybackState buildState(int state) {
        // Hard code the current position and length of track to simplify the code
        return new PlaybackState.Builder().setActions(
                PlaybackState.ACTION_PLAY | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                        | PlaybackState.ACTION_SKIP_TO_NEXT
                        | PlaybackState.ACTION_PLAY_FROM_MEDIA_ID
                        | PlaybackState.ACTION_PLAY_PAUSE)
                .setState(state, mMediaPlayer.getCurrentPosition(), 1, SystemClock.elapsedRealtime())
                .build();
    }

    //Helper method to start playing a track
    private void handlePlay() {
        //handle the mediasession state
        mSession.setPlaybackState(buildState(PlaybackState.STATE_PLAYING));
        //Update the session with the track meta-data
        mSession.setMetadata(mCurrentTrack);

        try {
            //Pass the music url to the MediaPlayer
            mMediaPlayer.reset();
            mMediaPlayer.setDataSource(MyMusicService.this,
                    Uri.parse(mCurrentTrack.getDescription().getMediaId()));
        } catch (IOException e) {
            e.printStackTrace();
        }

        //Set a callback for when the music is ready to be played
        mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                //Start the playback now that the track is ready
                mp.start();
            }
        });

        //Tell the mediaplayer to start downloading the tracl
        mMediaPlayer.prepareAsync();
    }

    @Override
    public void onDestroy() {
        mSession.release();
    }

    @Override
    public BrowserRoot onGetRoot(String clientPackageName, int clientUid, Bundle rootHints) {
        // Perform any checks here to only allow Android Auto, Android Wear, etc to get access
        return new BrowserRoot("root", null);
    }

    //Build up a tree of all available music from the list prepared earlier
    @Override
    public void onLoadChildren(final String parentMediaId, final Result<List<MediaBrowser.MediaItem>> result) {
        List<MediaBrowser.MediaItem> list = new ArrayList<>();
        for (MediaMetadata m : mMusic) {
            list.add(new MediaBrowser.MediaItem(m.getDescription(), MediaItem.FLAG_PLAYABLE));
        }
        result.sendResult(list);
    }

    private final class MediaSessionCallback extends MediaSession.Callback {
        // Play button pressed by the user
        @Override
        public void onPlay() {
            if (mCurrentTrack == null) {
                //No current song is selected, so pick the first one and start playing it
                mCurrentTrack = mMusic.get(0);
                handlePlay();
            } else {
                //Current song is ready but paused, so start playing the music
                mMediaPlayer.start();
                //Update the UI to show we are playing
                mSession.setPlaybackState(buildState(PlaybackState.STATE_PLAYING));
            }
        }

        //Search through entire music library
        @Override
        public void onPlayFromMediaId(String mediaId, Bundle extras) {
            for (MediaMetadata item : mMusic) {
                if (item.getDescription().getMediaId().equals(mediaId)) {
                    mCurrentTrack = item;
                    break;
                }
            }
            handlePlay();
        }

        //Pause button pressed by the user
        @Override
        public void onPause() {
            //Pause the music playback
            mMediaPlayer.pause();
            //Update the UI to show we are paused
            mSession.setPlaybackState(buildState(PlaybackState.STATE_PAUSED));
        }
    }
}
