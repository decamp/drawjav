/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav;

import java.io.IOException;

import bits.util.Guid;

/**
 * @author decamp
 */
public class BasicStreamHandle implements Stream {

    private final Guid         mGuid;
    private final int          mType;
    private final StreamFormat mPictureFormat;
    private final AudioFormat  mAudioFormat;


    public BasicStreamHandle( int type,
                              StreamFormat pictureFormat,
                              AudioFormat audioFormat )
    {
        this( null, type, pictureFormat, audioFormat );
    }


    public BasicStreamHandle( Guid optGuid,
                              int type,
                              StreamFormat pictureFormat,
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
    
    public StreamFormat format() {
        return mPictureFormat;
    }
    
    public AudioFormat audioFormat() {
        return mAudioFormat;
    }

    public boolean isOpen() {
        return false;
    }
    
    public void close() throws IOException {}

}
