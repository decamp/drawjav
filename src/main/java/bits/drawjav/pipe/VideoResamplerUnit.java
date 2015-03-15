///*
// * Copyright (c) 2014. Massachusetts Institute of Technology
// * Released under the BSD 2-Clause License
// * http://opensource.org/licenses/BSD-2-Clause
// */
//
//package bits.drawjav.pipe;
//
//import bits.drawjav.*;
//import bits.drawjav.video.OneFormatVideoAllocator;
//import bits.drawjav.video.VideoAllocator;
//import bits.jav.Jav;
//import bits.jav.JavException;
//import bits.jav.swscale.SwsContext;
//
//
///**
// * Manages JavFrame conversion between PictureFormats.
// *
// * @author decamp
// */
//public class VideoResamplerUnit implements AvUnit {
//
//    private final InHandler mInPad = new InHandler();
//    private final OutHandler mOutPad = new OutHandler();
//
//    private final MemoryManager mOptMem;
//
//    private boolean mOpen = false;
//
//    private
//
//    private StreamFormat mPredictedSourceFormat = null;
//    private StreamFormat mSourceFormat          = null;
//    private StreamFormat mRequestedFormat       = null;
//    private Stream       mDestStream           = null;
//    private StreamFormat mDestFormat           = null;
//    private int          mConversionFlags      = Jav.SWS_FAST_BILINEAR;
//
//    private boolean    mNeedsInit = false;
//    private SwsContext mConverter = null;
//
//    private boolean mDisposed = false;
//
//
//    public VideoResamplerUnit( VideoAllocator alloc ) {
//        if( alloc == null ) {
//            mAlloc = OneFormatVideoAllocator.createPacketLimited( 8 );
//        } else {
//            mAlloc = alloc;
//            alloc.ref();
//        }
//    }
//
//
//    public StreamFormat sourceFormat() {
//        return mSourceFormat != null ? mSourceFormat : mPredictedSourceFormat;
//    }
//
//
//    public void sourceFormat( StreamFormat format ) {
//        if( format == mPredictedSourceFormat || format != null && format.equals( mPredictedSourceFormat ) ) {
//            mPredictedSourceFormat = format;
//            mSourceFormat = null;
//            return;
//        }
//
//        mPredictedSourceFormat = format;
//        mSourceFormat = null;
//        mNeedsInit = true;
//
//        // Source format may affect destination format if requested format is
//        // partially defined.
//        updateDestFormat();
//    }
//
//    /**
//     * @return destination format requested by user. May be partially defined.
//     */
//    public StreamFormat requestedFormat() {
//        return mRequestedFormat;
//    }
//
//    /**
//     * @return computed destination format. May be different from {@code #requestedFormat()}.
//     */
//    public StreamFormat requestFormat() {
//        return mDestFormat;
//    }
//
//
//    public void requestFormat( StreamFormat format ) {
//        // Assign format == mRequestedFormat either way.
//        // Better to use identical objects than merely equivalent objects.
//        if( format == mRequestedFormat || format != null && format.equals( mRequestedFormat ) ) {
//            mRequestedFormat = format;
//        } else {
//            mRequestedFormat = format;
//            updateDestFormat();
//        }
//    }
//
//
//    public int conversionFlags() {
//        return mConversionFlags;
//    }
//
//
//    public void conversionFlags( int flags ) {
//        if( flags == mConversionFlags ) {
//            return;
//        }
//        mConversionFlags = flags;
//        mNeedsInit = true;
//    }
//
//
//    public DrawPacket convert( DrawPacket source ) throws JavException {
//        if( source.isGap() ) {
//            source.ref();
//            return source;
//        }
//
//        if( mSourceFormat == null || !mSourceFormat.matches( source ) ) {
//            mSourceFormat = StreamFormat.fromVideoPacket( source );
//            mNeedsInit = true;
//            updateDestFormat();
//        }
//
//        if( mNeedsInit ) {
//            init();
//        }
//
//        if( mConverter == null ) {
//            source.ref();
//            return source;
//        }
//
//        DrawPacket dest = mAlloc.alloc( mDestFormat );
//        dest.init( mDestStream, source.startMicros(), source.stopMicros(), false );
//        mConverter.conv( source, dest );
//        return dest;
//    }
//
//
//    public void close() {
//        if( mDisposed ) {
//            return;
//        }
//
//        mDisposed = true;
//        mAlloc.deref();
//        if( mConverter != null ) {
//            mConverter.release();
//            mConverter = null;
//        }
//    }
//
//
//
//    private void updateDestFormat() {
//        StreamFormat source = mSourceFormat != null ? mSourceFormat : mPredictedSourceFormat;
//        StreamFormat dest = StreamFormat.merge( source, mRequestedFormat );
//        if( dest.equals( mDestFormat ) ) {
//            return;
//        }
//        mDestFormat = dest;
//        mDestStream = new BasicStream( mDestFormat );
//        formatChanged();
//    }
//
//
//    private void init() throws JavException {
//        mNeedsInit = false;
//        StreamFormat src = mSourceFormat;
//        StreamFormat dst = mDestFormat;
//
//        if( src == null || dst == null || !src.isFullyDefined() || !dst.isFullyDefined() ) {
//            return;
//        }
//
//        if( dst.equals( src ) ) {
//            return;
//        }
//
//        mConverter = SwsContext.allocAndInit( src.mWidth,
//                                              src.mHeight,
//                                              src.mPixelFormat,
//                                              dst.mWidth,
//                                              dst.mHeight,
//                                              dst.mPixelFormat,
//                                              mConversionFlags );
//    }
//
//
//    private void formatChanged() {
//        if( mNeedsInit ) {
//            return;
//        }
//        if( mConverter != null ) {
//            mConverter.release();
//            mConverter = null;
//        }
//
//        mNeedsInit = true;
//    }
//
//
//    @Deprecated public void setSourceFormat( StreamFormat format ) {
//        sourceFormat( format );
//    }
//
//
//    @Deprecated public void setDestFormat( StreamFormat format ) {
//        mRequestedFormat = format;
//        updateDestFormat();
//    }
//
//
//    @Deprecated public StreamFormat getDestFormat() {
//        return requestFormat();
//    }
//
//
//    @Deprecated public void setConversionFlags( int flags ) {
//        conversionFlags( flags );
//    }
//
//}
