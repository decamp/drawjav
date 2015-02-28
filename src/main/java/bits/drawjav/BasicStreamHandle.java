/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav;

import java.io.IOException;

import bits.drawjav.audio.AudioFormat;
import bits.drawjav.video.PictureFormat;
import bits.util.Guid;

/**
 * @author decamp
 */
public class BasicStreamHandle implements StreamHandle {

    private final Guid          mGuid;
    private final int           mType;
    private final PictureFormat mPictureFormat;
    private final AudioFormat   mAudioFormat;


    public BasicStreamHandle( int type,
                              PictureFormat pictureFormat,
                              AudioFormat audioFormat )
    {
        this( null, type, pictureFormat, audioFormat );
    }


    public BasicStreamHandle( Guid optGuid,
                              int type,
                              PictureFormat pictureFormat,
                              AudioFormat audioFormat )
    {
        mGuid          = optGuid != null ? optGuid : Guid.create();
        mType          = type;
        mPictureFormat = pictureFormat;
        mAudioFormat   = audioFormat;
    }


    public Guid guid() {
        return mGuid;
    }
    
    public int type() {
        return mType;
    }
    
    public PictureFormat pictureFormat() {
        return mPictureFormat;
    }
    
    public AudioFormat audioFormat() {
        return mAudioFormat;
    }

    public boolean isOpen() {
        return false;
    }
    
    public void close() throws IOException {}
    
    
    public boolean equals( Object o ) {
        if( !( o instanceof StreamHandle ) ) {
            return false;
        }
        return mGuid.equals( ((StreamHandle)o).guid() );
    }
    
    public int hashCode() {
        return mGuid.hashCode();
    }

}
