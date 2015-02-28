/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav.video;

import bits.drawjav.DrawPacket;
import bits.jav.*;
import bits.jav.swscale.*;


/**
 * Manages JavFrame conversion between PictureFormats.
 * 
 * @author decamp
 */
public class VideoPacketResampler {

    private final VideoAllocator mAlloc;

    private PictureFormat mPredictedSourceFormat = null;
    private PictureFormat mSourceFormat          = null;
    private PictureFormat mRequestedFormat       = null;
    private PictureFormat mDestFormat            = null;
    private int           mConversionFlags       = Jav.SWS_FAST_BILINEAR;

    private boolean    mNeedsInit = false;
    private SwsContext mConverter = null;

    private boolean mDisposed = false;


    public VideoPacketResampler( VideoAllocator alloc ) {
        if( alloc == null ) {
            mAlloc = OneStreamVideoAllocator.createPacketLimited( 8 );
        } else {
            mAlloc = alloc;
            alloc.ref();
        }
    }


    public PictureFormat sourceFormat() {
        return mSourceFormat != null ? mSourceFormat : mPredictedSourceFormat;
    }


    public void sourceFormat( PictureFormat format ) {
        if( format == mPredictedSourceFormat || format != null && format.equals( mPredictedSourceFormat ) ) {
            mPredictedSourceFormat = format;
            mSourceFormat = null;
            return;
        }

        mPredictedSourceFormat = format;
        mSourceFormat = null;
        mNeedsInit = true;

        // Source format may affect destination format if requested format is
        // partially defined.
        updateDestFormat();
    }

    /**
     * @return destination format requested by user. May be partially defined.
     */
    public PictureFormat requestedFormat() {
        return mRequestedFormat;
    }

    /**
     * @return computed destination format. May be different from {@code #requestedFormat()}.
     */
    public PictureFormat destFormat() {
        return mDestFormat;
    }


    public void destFormat( PictureFormat format ) {
        // Assign format == mRequestedFormat either way.
        // Better to use identical objects than merely equivalent objects.
        if( format == mRequestedFormat || format != null && format.equals( mRequestedFormat ) ) {
            mRequestedFormat = format;
        } else {
            mRequestedFormat = format;
            updateDestFormat();
        }
    }


    public int conversionFlags() {
        return mConversionFlags;
    }


    public void conversionFlags( int flags ) {
        if( flags == mConversionFlags ) {
            return;
        }
        mConversionFlags = flags;
        mNeedsInit = true;
    }


    public DrawPacket convert( DrawPacket source ) throws JavException {
        if( source.isGap() ) {
            return source;
        }

        if( mSourceFormat == null || !mSourceFormat.matches( source ) ) {
            mSourceFormat = source.toPictureFormat();
            mNeedsInit = true;
            updateDestFormat();
        }

        if( mNeedsInit ) {
            init();
        }

        if( mConverter == null ) {
            source.ref();
            return source;
        }

        DrawPacket dest = mAlloc.alloc( mDestFormat );
        dest.init( source.stream(), source.startMicros(), source.stopMicros(), mDestFormat, false );
        mConverter.conv( source, dest );
        return dest;
    }


    public void close() {
        if( mDisposed ) {
            return;
        }

        mDisposed = true;
        mAlloc.deref();
        if( mConverter != null ) {
            mConverter.release();
            mConverter = null;
        }
    }



    private void updateDestFormat() {
        PictureFormat source = mSourceFormat != null ? mSourceFormat : mPredictedSourceFormat;
        PictureFormat dest = PictureFormat.merge( source, mRequestedFormat );
        if( dest.equals( mDestFormat ) ) {
            return;
        }
        mDestFormat = dest;
        formatChanged();
    }


    private void init() throws JavException {
        mNeedsInit = false;
        PictureFormat src = mSourceFormat;
        PictureFormat dst = mDestFormat;
        
        if( !PictureFormat.isFullyDefined( src ) || !PictureFormat.isFullyDefined( dst ) ) {
            return;
        }

        if( dst.equals( src ) ) {
            return;
        }
        
        mConverter = SwsContext.allocAndInit( src.width(),
                                              src.height(),
                                              src.pixelFormat(),
                                              dst.width(),
                                              dst.height(),
                                              dst.pixelFormat(),
                                              mConversionFlags );
    }


    private void formatChanged() {
        if( mNeedsInit ) {
            return;
        }
        if( mConverter != null ) {
            mConverter.release();
            mConverter = null;
        }

        mNeedsInit = true;
    }


    @Deprecated public void setSourceFormat( PictureFormat format ) {
        sourceFormat( format );
    }


    @Deprecated public void setDestFormat( PictureFormat format ) {
        mRequestedFormat = format;
        updateDestFormat();
    }


    @Deprecated public PictureFormat getDestFormat() {
        return destFormat();
    }


    @Deprecated public void setConversionFlags( int flags ) {
        conversionFlags( flags );
    }

}
