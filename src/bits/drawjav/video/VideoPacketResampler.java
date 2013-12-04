package bits.drawjav.video;

import bits.drawjav.*;
import bits.jav.*;
import bits.jav.swscale.*;
import bits.jav.util.Rational;


/**
 * Manages JavFrame conversion between PictureFormats.
 * 
 * @author decamp
 */
public class VideoPacketResampler {

    private final int          mPoolCapacity;

    private VideoPacketFactory mFactory         = null;
    private PictureFormat      mSourceFormat    = null;
    private PictureFormat      mRequestedFormat = null;
    private PictureFormat      mDestFormat      = null;

    private Rational           mSampleAspect    = null;
    private int                mConversionFlags = Jav.SWS_FAST_BILINEAR;
    private int                mCropTop         = 0;
    private int                mCropBottom      = 0;

    private boolean            mNeedsInit       = false;
    private SwsContext         mConverter       = null;


    public VideoPacketResampler( int poolCapacity ) {
        mPoolCapacity = poolCapacity;
    }



    public VideoPacket process( VideoPacket source ) throws JavException {
        PictureFormat fmt = source.pictureFormat();
        if( fmt != mSourceFormat ) {
            setSourceFormat( fmt );
        }
        
        if( mNeedsInit ) {
            init();
        }
        
        if( mConverter == null ) {
            source.ref();
            return source;
        }
        
        VideoPacket dest = mFactory.build( source.stream(), source.startMicros(), source.stopMicros() );
        mConverter.convert( source, mCropTop, mSourceFormat.height() - mCropBottom - mCropTop, dest );
        return dest;
    }
    
    

    public void setSourceFormat( PictureFormat format ) {
        if( format == mSourceFormat || format != null && format.equals( mSourceFormat ) ) {
            mSourceFormat = format;
            return;
        }

        mSourceFormat = format;
        mNeedsInit = true;

        // Source format may affect destination format if requested format is
        // partially defined.
        updateDestFormat();
    }


    public void setDestFormat( PictureFormat format ) {
        mRequestedFormat = format;
        updateDestFormat();
    }


    public void setCrop( int top, int bottom ) {
        if( top == mCropTop && bottom == mCropBottom ) {
            return;
        }
        mCropTop = top;
        mCropBottom = bottom;
        mNeedsInit = true;
        
        // Source format map affect destination format if requested format is
        // partially defined.
        updateDestFormat();
    }


    public void setConversionFlags( int flags ) {
        if( flags == mConversionFlags ) {
            return;
        }
        mConversionFlags = flags;
        mNeedsInit = true;
    }

    
    public PictureFormat getDestFormat() {
        return mDestFormat;
    }


    public void close() {
        if( mFactory != null ) {
            mFactory.close();
            mFactory = null;
        }
        mConverter = null;
        mNeedsInit = true;
    }

    
    
    private void updateDestFormat() {
        PictureFormat format = PictureFormats.merge( mSourceFormat, mCropTop, mCropBottom, mRequestedFormat );
        if( format == null || !PictureFormats.isFullyDefined( format ) ) {
            return;
        }
        if( format.equals( mDestFormat ) ) {
            return;
        }

        mDestFormat = format;
        close();
    }


    private void init() throws JavException {
        mNeedsInit = false;
        
        PictureFormat src = mSourceFormat;
        PictureFormat dst = mDestFormat;
        
        if( !PictureFormats.isFullyDefined( src ) || !PictureFormats.isFullyDefined( dst ) ) {
            return;
        }

        if( mCropTop != 0 || mCropBottom != 0 ) {
            src = new PictureFormat( src.width(), src.height() - mCropBottom, src.pixelFormat() );
        }
        
        if( dst.equals( src ) ) {
            return;
        }
        
        mConverter = SwsContext.alloc();
        mConverter.configure( src.width(), src.height(), src.pixelFormat(),
                              dst.width(), dst.height(), dst.pixelFormat(),
                              mConversionFlags );
        mConverter.initialize();

        if( mFactory == null ) {
            mFactory = new VideoPacketFactory( mDestFormat, mPoolCapacity );
        }
    }
    
    
}
