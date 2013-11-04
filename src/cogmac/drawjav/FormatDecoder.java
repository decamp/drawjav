package cogmac.drawjav;

import java.io.*;
import java.nio.*;
import java.util.*;

import bits.data.Guid;
import bits.jav.*;
import bits.jav.codec.*;
import bits.jav.format.*;
import bits.jav.util.Rational;
import bits.langx.ref.*;
import cogmac.drawjav.audio.*;


/**
 * Not thread-safe.
 * 
 * @author decamp
 */
public class FormatDecoder implements Source {
    
    
    public static FormatDecoder openFile( File file ) throws IOException {
        return openFile( file, false, 0L );
    }
    
    
    public static FormatDecoder openFile( File file, 
                                          boolean overrideStartMicros,
                                          long startMicros ) 
                                          throws IOException {
        JavLibrary.init();
        if( !file.exists() ) {
            throw new FileNotFoundException();
        }
        JavFormatContext format = JavFormatContext.openInputFile( file );
        return new FormatDecoder( format, overrideStartMicros, startMicros );
    }
    
    
    public static FormatDecoder open( JavFormatContext format ) throws IOException {
        return open( format, false, 0L );
    }
    
    
    public static FormatDecoder open( JavFormatContext format,
                                      boolean overrideStartMicros,
                                      long startMicros )
                                      throws IOException 
    {
        JavLibrary.init();
        return new FormatDecoder( format, overrideStartMicros, startMicros );
    }
    
    
    
    private final JavFormatContext mFormat;
    private final JavPacket mPacket;
    private final Stream[] mStreams;
    
    private int mFirstStreamIndex = -1;
    private Stream mDefaultStream = null;
    
    
    private FormatDecoder( JavFormatContext format, 
                           boolean overrideStartMicros, 
                           long startMicros ) 
    {
        mFormat   = format;
        mPacket   = JavPacket.newInstance();
        mStreams  = new Stream[format.streamCount()];
        
        long firstMicros       = Long.MAX_VALUE;
        long firstPts          = Long.MAX_VALUE;
        Rational firstTimeBase = null;
        
        for( int i = 0; i < mStreams.length; i++ ) {
            JavStream s   = format.stream( i );
            long pts      = s.startTime();
            Rational tb   = s.timeBase();
            Rational conv = Rational.reduce( tb.num() * 1000000, tb.den() );
            long micros   = pts * conv.num() / conv.den();
            int type      = s.codecContext().codecType();
            
            if( type == JavConstants.AVMEDIA_TYPE_VIDEO || type == JavConstants.AVMEDIA_TYPE_AUDIO ) {
                if( i == 0 || micros < firstMicros ) {
                    firstPts          = pts;
                    firstTimeBase     = tb;
                    firstMicros       = micros;
                    mFirstStreamIndex = i;
                }
            }
        }
        
        //TODO: Redundant code.
        for( int i = 0; i < mStreams.length; i++ ) {
            JavStream s   = format.stream( i );
            Stream ss     = null;
            long startPts = 0;
            
            if( overrideStartMicros ) {
                Rational tb = s.timeBase();
                
                if( tb.equals( firstTimeBase ) ) {
                    startPts = firstPts;
                } else {
                    startPts = firstPts * firstTimeBase.num() * tb.den() / ( firstTimeBase.den() * tb.num() );
                }
            }
            
            switch( s.codecContext().codecType() ) {
            case JavConstants.AVMEDIA_TYPE_VIDEO:
                ss = new VideoStream( s, startPts, startMicros );
                break;
                
            case JavConstants.AVMEDIA_TYPE_AUDIO:
                ss = new AudioStream( s, startPts, startMicros );
                break;
                
            default:
                ss = new NullStream( s );
                break;
            }
            
            mStreams[i] = ss;
        }
        
        if( mFirstStreamIndex != -1 ) {
            mDefaultStream = mStreams[mFirstStreamIndex];
        }
        
    }
    
    
    
    public int streamCount() {
        return mStreams.length;
    }
    
    
    public StreamHandle stream( int index ) {
        return mStreams[index];
    }

    
    public StreamHandle stream( Guid guid ) {
        for( Stream s: mStreams ) {
            if( s.guid().equals( guid ) ) {
                return s;
            }
        }
        
        return null;
    }
    
    
    public StreamHandle stream( int mediaType, int index ) {
        for( int i = 0; i < mStreams.length; i++ ) {
            if( mStreams[i].type() == mediaType ) {
                if( index-- == 0 ) {
                    return mStreams[i];
                }
            }
        }
        return null;
    }
    
    
    @SuppressWarnings( { "rawtypes", "unchecked" } )
    public List<StreamHandle> streams() {
        return (List)Arrays.asList( mStreams );
    }
    
    
    /**
     * @deprecated
     */
    public StreamHandle firstStream( int mediaType ) {
        return stream( mediaType, 0 );
    }
    
    
    public JavFormatContext formatContext() {
        return mFormat;
    }
    
    
    public void openStream( StreamHandle stream ) throws JavException {
        openStream( stream, 32 );
    }
    
    
    public void openStream( StreamHandle stream, int poolSize ) throws JavException {
        Stream ss = mStreams[ ((Stream)stream).index() ];
        
        synchronized( this ) {
            if( ss.isOpen() ) {
                return;
            }            
            ss.open( poolSize );
            updateDefaultStream();
        }
    }
    
    
    public void closeStream( StreamHandle stream ) throws IOException {
        Stream ss = mStreams[ ((Stream)stream).index() ];
        synchronized( this ) {
            if( !ss.isOpen() ) {
                return;
            }
            ss.close();
            updateDefaultStream();
        }
    }
    
    
    public void seek( long micros ) throws JavException {
        Stream stream = mDefaultStream;
        if( stream == null ) {
            return;
        }
        
        Rational timeBase = stream.timeBase();
        long pts          = stream.microsToPts( micros );
        long startPts     = stream.javStream().startTime();
        
        mFormat.seek( stream.index(), pts, 0 );
        
        for(int i = 0; i < mStreams.length; i++) {
            Stream ss = mStreams[i];
            
            if(timeBase.equals(ss.timeBase())) {
                ss.seekPts(pts - startPts + ss.javStream().startTime());
            }else{
                ss.seekMicros(micros);
            }
        }
    }
    
    
    public Packet readNext() throws IOException {
        if( mFormat.readPacket( mPacket ) != 0 ) {
            //Probably EOF?
            throw new EOFException();
        }
        
        int idx = mPacket.streamIndex();
        if( idx < 0 || idx >= mStreams.length ) {
            return null;
        }
        
        return mStreams[idx].process( mPacket );
    }
    
    
    public boolean isOpen() {
        return mDefaultStream != null;
    }
    
    
    public void close() throws IOException {
        for(int i = 0; i < mStreams.length; i++) {
            try {
                Stream ss = mStreams[i];
                if(ss == null || !ss.isOpen())
                    continue;
                
                ss.close();
            }catch(Exception ex) {}
        }
        
        updateDefaultStream();
    }
    
    
    public void overrideTimestamps( long mediaStartMicros ) {
        if( mFirstStreamIndex < 0 ) {
            return;
        }
        
        JavStream ss       = mFormat.stream( mFirstStreamIndex );
        Rational firstBase = ss.timeBase();
        long firstPts      = ss.startTime();
        
        for( Stream s: mStreams ) {
            Rational tb   = s.timeBase();
            long startPts = firstPts;
            
            if( tb.equals( firstBase ) ) {
                startPts = firstPts;
            } else {
                startPts = firstPts * firstBase.num() * tb.den() / ( firstBase.den() * tb.num() );
            }
            
            s.initTimer( startPts, mediaStartMicros );
        }
    }
    
    
    public void useEmbeddedTimestamps(long offsetMicros) {
        for(Stream s: mStreams) {
            s.initTimer(0, offsetMicros);
        }
    }
    
    
    
    
    /**
     * Determines which stream should be used by default.
     */
    private synchronized void updateDefaultStream() {
//        Stream stream = null;
//        int score     = 0;
//
//        for(Stream ss: mStreams) {
//            if(ss == null || !ss.isOpen())
//                continue;
//            
//            int type    = ss.javStream().codecContext().codecType();
//            int sc      = 1;
//            
//            switch(type) {
//            case AVMEDIA_TYPE_VIDEO:
//                mDefaultStream = ss;
//                return;
//                
//            case AVMEDIA_TYPE_AUDIO:
//                sc = 2;
//                break;
//                
//            default:
//                sc = 1;
//                break;
//            }
//            
//            if(sc > score) {
//                stream = ss;
//                score  = sc;
//            }
//        }
//        
//        mDefaultStream = stream;
    }
    
        
    
    private static abstract class Stream implements StreamHandle {
        
        final JavStream mStream;
        final Guid mGuid;
        final int mIndex;
        final int mType;
        final Rational mTimeBase;
        final PacketTimer mTimer;
        
        Stream( JavStream stream ) {
            mStream   = stream;
            mGuid     = Guid.newInstance();
            mIndex    = stream.index();
            mType     = stream.codecContext().codecType();
            mTimeBase = stream.timeBase();
            mTimer    = new PacketTimer( mTimeBase );
        }
        

        
        public Guid guid() {
            return mGuid;
        }
        
        
        public int type() {
            return mType;
        }
        
        
        public PictureFormat pictureFormat() {
            return null;
        }
        
        
        public AudioFormat audioFormat() {
            return null;
        }
        
        
        JavStream javStream() {
            return mStream;
        }
        
        
        int index() {
            return mIndex;
        }
        
        
        Rational timeBase() {
            return mTimeBase;
        }
        
        
        void initTimer( long startSrcPts, long startDstMicros ) {
            mTimer.init( startSrcPts, startDstMicros );
        }
        
        
        public long ptsToMicros( long pts ) {
            return mTimer.ptsToMicros( pts );
        }
        
        
        public long microsToPts( long micros ) {
            return mTimer.microsToPts( micros );
        }

        
        
        public abstract boolean isOpen();
        public abstract void close() throws IOException;

        abstract void open( int poolSize ) throws JavException;
        abstract Packet process( JavPacket packet ) throws IOException;
        abstract void seekPts( long pts );
        abstract void seekMicros( long micros );
        
        
        @Override
        public int hashCode() {
            return mGuid.hashCode();
        }
    
    }

    
    
    private static class VideoStream extends Stream {
        
        private final JavCodecContext mCodecContext;
        private final PictureFormat mPictureFormat;
        
        private final long[] mRange = new long[2];
        
        private boolean mIsOpen                = false;
        private HardRefPool<VideoPacket> mPool = null;
        private boolean mHasKeyFrame           = false;
        private VideoPacket mCurrentFrame      = null;
        
        
        public VideoStream( JavStream stream,
                            long startPts, 
                            long startMicros) 
        {
            super( stream );
            mCodecContext  = stream.codecContext();
            mPictureFormat = PictureFormat.fromCodecContext( mCodecContext );
            initTimer( startPts, startMicros );
        }
        
        
        
        @Override
        public PictureFormat pictureFormat() {
            return mPictureFormat;
        }
        
        
        public void open( int poolSize ) throws JavException {
            if( mIsOpen ) { 
                return;
            }
            
            if( !mCodecContext.isOpen() ) {
                JavCodec codec = JavCodec.findDecoder( mCodecContext.codecId() );
                if( codec == null ) {
                    throw new JavException( JavConstants.AVERROR_DECODER_NOT_FOUND,
                                            "Codec not found for stream: " + mStream.index() );
                }
                mCodecContext.open( codec );
            }

            mPool   = new HardRefPool<VideoPacket>( poolSize );
            mIsOpen = true;
        }

        
        public boolean isOpen() {
            return mIsOpen;
        }
        
        
        public void close() {
            if( !mIsOpen ) {
                return;
            }
            
            mIsOpen = false;
            if( mCodecContext.isOpen() ) {
                try {
                    mCodecContext.close();
                } catch(Exception ex) {}
            }
            
            if( mPool != null ) {
                mPool.close();
                mPool = null;
            }
            
            mHasKeyFrame = false;
            if( mCurrentFrame != null ) {
                mCurrentFrame.deref();
                mCurrentFrame = null;
            }
        }
        
        
        public Packet process( JavPacket packet ) throws IOException {
            long pts      = packet.presentTime();
            long duration = packet.duration();
            
            if( !mIsOpen ) {
                mTimer.packetSkipped( pts, duration, mRange );
                return null;
            }
            
            VideoPacket ret = mCurrentFrame;
            mCurrentFrame   = null;
            
            if( ret == null ) {
                ret = mPool.poll();
                
                if( ret == null ) {
                    ret = VideoPacket.newAutoInstance( mPool );
                }
            }
            
            boolean finished = mCodecContext.decodeVideo( packet, ret );
            
            if( !finished ) {
                mCurrentFrame = ret;
                return null;
            }
            
            mTimer.packetDecoded( ret.bestEffortTimestamp(), ret.packetDuration(), mRange );
            
            if( !mHasKeyFrame && !ret.isKeyFrame() ) {
                ret.deref();
                return null;
            }
            
            mHasKeyFrame = true;
            ret.init( this, mPictureFormat, mRange[0], mRange[1] );
            
            return ret;
        }
        
        
        public void seekPts( long pts ) {
            mTimer.seekPts( pts );
            if( mIsOpen ) {
                mCodecContext.flush();
            }
        }
        
        
        public void seekMicros( long micros ) {
            mTimer.seekMicros( micros );
            if( mIsOpen ) {
                mCodecContext.flush();
            }
        }
        
    }


    
    private static class AudioStream extends Stream {
        
        private final JavCodecContext mCodecContext;
        private final AudioFormat mFormat;
        private final AudioSampleFormat mSampleFormat;
        
        private final long[] mRange = new long[2];
        
        private boolean mIsOpen                = false;
        private ByteBuffer mBuffer             = null;
        
        private HardRefPool<AudioPacket> mPool = null;
        private AudioPacket mCurrentFrame      = null;
        
        
        public AudioStream( JavStream stream,
                            long startPts, 
                            long startMicros ) 
        {
            super(stream);
            
            mCodecContext = stream.codecContext();
            mFormat       = AudioFormat.fromCodecContext(mCodecContext);
            mSampleFormat = AudioSampleFormat.formatFromCode(mFormat.sampleFormat());
    
            initTimer(startPts, startMicros);
        }
        
        
                
        public AudioFormat audioFormat() {
            return mFormat;
        }
        
        
        public void open( int poolSize ) throws JavException {
            if( mIsOpen ) {
                return;
            }
            if( !mCodecContext.isOpen() ) {
                JavCodec codec = JavCodec.findDecoder( mCodecContext.codecId() );
                if( codec == null ) {
                    throw new JavException( JavConstants.AVERROR_DECODER_NOT_FOUND,
                                            "Codec not found for stream: " + mStream.index() );
                }
                mCodecContext.open( codec );
            }
            
            mPool   = new HardRefPool<AudioPacket>( poolSize );
            mBuffer = ByteBuffer.allocateDirect( JavConstants.AVCODEC_MAX_AUDIO_FRAME_SIZE );
            mBuffer.order( ByteOrder.nativeOrder() );
            mIsOpen = true;
        }
        
        
        public boolean isOpen() {
            return mIsOpen;
        }
        
        
        public void close() {
            mPool   = null;
            mBuffer = null;
            
            if( mCodecContext.isOpen() ) {
                try {
                    mCodecContext.close();
                }catch(Exception ex) {}
            }
        }
        
        
        public AudioPacket process( JavPacket packet ) throws IOException {
            //System.out.println(packet.decodeTime() + "\t" + packet.presentTime() + "\t" + packet.duration());
            if( mPool == null ) {
                mTimer.packetSkipped( packet.presentTime(), packet.duration(), mRange );
                return null;
            }

            AudioPacket ret = mCurrentFrame;
            mCurrentFrame   = null;
            
            if( ret == null ) {
                ret = mPool.poll();
                if( ret == null ) {
                    ret = AudioPacket.newAutoInstance( mPool );
                }
            }
            
            boolean finished = mCodecContext.decodeVideo( packet, ret );
            if( !finished ) {
                mCurrentFrame = ret;
                return null;
            }
            
            mTimer.packetDecoded( ret.bestEffortTimestamp(), ret.packetDuration(), mRange );
            ret.init( this, mFormat, mRange[0], mRange[1] );
            
            return ret;
        }
        
        
        public void seekPts( long pts ) {
            mTimer.seekPts( pts );
            if( mIsOpen ) {
                mCodecContext.flush();
            }
        }
        
        
        public void seekMicros( long micros ) {
            mTimer.seekMicros( micros );
            if( mIsOpen ) {
                mCodecContext.flush();
            }
        }
        
    }
    

    
    private static class NullStream extends Stream {
        
        private final long[] mRange = new long[2];
        
        
        public NullStream( JavStream stream ) {
            super( stream );
        }
        
        
        
        public boolean isOpen() {
            return false;
        }
        
        
        public void open( int poolSize ) throws JavException {
            throw new JavException( "Cannot open stream of type: " + mType );
        }
        
        
        public void close() {}
        
        
        public Packet process( JavPacket packet ) throws IOException {
            long pts      = packet.presentTime();
            long duration = packet.duration();
            
            mTimer.packetSkipped( pts, duration, mRange );
            return null;
        }
        

        public void seekPts( long pts ) {
            mTimer.seekPts( pts );
        }
        
        
        public void seekMicros( long micros ) {
            mTimer.seekMicros( micros );
        }
        
    }


}

