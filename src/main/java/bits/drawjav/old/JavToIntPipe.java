/*
* Copyright (c) 2014. Massachusetts Institute of Technology
* Released under the BSD 2-Clause License
* http://opensource.org/licenses/BSD-2-Clause
*/

package bits.drawjav.old;

import java.io.IOException;

import bits.drawjav.*;
import bits.drawjav.video.IntFrame;
import bits.drawjav.video.VideoPackets;
import bits.util.ref.*;

/**
* Converts JavFrames to IntFrames in video pipeline.
*
* @author decamp
*/
@Deprecated
public class JavToIntPipe implements Sink<DrawPacket> {

    private final ObjectPool<IntFrame> mPool = new SoftPool<IntFrame>( 3 );
    private final Sink<? super IntFrame> mSink;
    private final int                    mCropTop;
    private final int                    mCropBottom;


    public JavToIntPipe( Sink<? super IntFrame> sink ) {
        this( sink, 0, 0 );
    }


    public JavToIntPipe( Sink<? super IntFrame> sink, int cropTop, int cropBottom ) {
        mSink = sink;
        mCropTop = cropTop;
        mCropBottom = cropBottom;
    }


    public void consume( DrawPacket frame ) throws IOException {
        IntFrame ff = mPool.poll();
        if( ff == null ) {
            ff = new IntFrame( mPool );
        }

        if( mCropTop > 0 || mCropBottom > 0 ) {
            VideoPackets.toArgb( frame, mCropTop, frame.height() - mCropBottom, ff );
        } else {
            VideoPackets.toArgb( frame, false, ff );
        }

        try {
            mSink.consume( ff );
        } finally {
            ff.deref();
        }
    }


    public void clear() {
        mSink.clear();
    }


    public void close() throws IOException {
        mSink.close();
    }


    public boolean isOpen() {
        return true;
    }

}
