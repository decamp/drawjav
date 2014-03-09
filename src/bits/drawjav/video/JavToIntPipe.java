package bits.drawjav.video;

import java.io.IOException;

import bits.drawjav.*;
import bits.langx.ref.*;



/**
 * Converts JavFrames to IntFrames in video pipeline.
 * 
 * @author decamp
 */
public class JavToIntPipe implements Sink<VideoPacket> {

    private final RefPool<IntFrame> mPool = new SoftRefPool<IntFrame>( 3 );
    private final Sink<? super IntFrame> mSink;
    private final int mCropTop;
    private final int mCropBottom;


    public JavToIntPipe( Sink<? super IntFrame> sink ) {
        this( sink, 0, 0 );
    }


    public JavToIntPipe( Sink<? super IntFrame> sink, int cropTop, int cropBottom ) {
        mSink = sink;
        mCropTop = cropTop;
        mCropBottom = cropBottom;
    }



    public void consume( VideoPacket frame ) throws IOException {
        IntFrame ff = mPool.poll();
        if( ff == null ) {
            ff = new IntFrame( mPool );
        }

        if( mCropTop > 0 || mCropBottom > 0 ) {
            PictureFormat form = frame.pictureFormat();
            VideoPackets.toArgb( frame, mCropTop, form.height() - mCropBottom, ff );
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
