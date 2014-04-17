package bits.drawjav;

import java.io.*;
import java.util.*;

import bits.jav.*;
import static bits.jav.Jav.*;
import bits.jav.codec.*;
import bits.jav.format.*;
import bits.jav.util.Rational;
import bits.util.Guid;
import bits.util.ref.*;


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
                                                            throws IOException
    {
        Jav.init();
        if( !file.exists() ) {
            throw new FileNotFoundException();
        }
        JavFormatContext format = JavFormatContext.openInput( file );
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
        Jav.init();
        return new FormatDecoder( format, overrideStartMicros, startMicros );
    }



    private final JavFormatContext mFormat;
    private final JavPacket mPacket;
    private final JavPacket mNullPacket;
    private final Stream[] mStreams;

    private int mFirstStreamIndex = -1;
    private Stream mDefaultStream = null;

    private boolean mPacketValid = false;
    private boolean mEof = false;
    private int mFlushStream = -1;


    private FormatDecoder( JavFormatContext format,
                           boolean overrideStartMicros,
                           long startMicros )
    {
        mFormat = format;
        mPacket = JavPacket.alloc();
        mNullPacket = JavPacket.alloc();
        mStreams = new Stream[format.streamCount()];

        long firstMicros = Long.MAX_VALUE;
        long firstPts = Long.MAX_VALUE;
        Rational firstTimeBase = null;

        for( int i = 0; i < mStreams.length; i++ ) {
            JavStream s = format.stream( i );
            long pts = s.startTime();
            Rational tb = s.timeBase();
            Rational conv = Rational.reduce( tb.num() * 1000000, tb.den() );
            long micros = pts * conv.num() / conv.den();
            int type = s.codecContext().codecType();

            if( type == AVMEDIA_TYPE_VIDEO || type == AVMEDIA_TYPE_AUDIO ) {
                if( i == 0 || micros < firstMicros ) {
                    firstPts = pts;
                    firstTimeBase = tb;
                    firstMicros = micros;
                    mFirstStreamIndex = i;
                }
            }
        }

        for( int i = 0; i < mStreams.length; i++ ) {
            JavStream s = format.stream( i );
            Stream ss = null;
            long startPts = 0;

            if( overrideStartMicros ) {
                Rational tb = s.timeBase();

                if( tb.equals( firstTimeBase ) ) {
                    startPts = firstPts;
                } else {
                    startPts = firstPts * firstTimeBase.num() * tb.den() / (firstTimeBase.den() * tb.num());
                }
            }

            switch( s.codecContext().codecType() ) {
            case AVMEDIA_TYPE_VIDEO:
                ss = new VideoStream( s, startPts, startMicros );
                break;

            case AVMEDIA_TYPE_AUDIO:
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



    @Override
    public int streamCount() {
        return mStreams.length;
    }

    @Override
    public StreamHandle stream( int index ) {
        return mStreams[index];
    }

    @Override
    public StreamHandle stream( Guid guid ) {
        for( Stream s : mStreams ) {
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

    @Override
    @SuppressWarnings( { "rawtypes", "unchecked" } )
    public List<StreamHandle> streams() {
        return (List)Arrays.asList( mStreams );
    }


    public JavFormatContext formatContext() {
        return mFormat;
    }

    @Override
    public void openStream( StreamHandle stream ) throws JavException {
        openStream( stream, 32 );
    }


    public void openStream( StreamHandle stream, int poolSize ) throws JavException {
        Stream ss = mStreams[((Stream)stream).index()];

        synchronized( this ) {
            if( ss.isOpen() ) {
                return;
            }
            ss.open( poolSize );
            updateDefaultStream();
        }
    }

    @Override
    public void closeStream( StreamHandle stream ) throws IOException {
        Stream ss = mStreams[((Stream)stream).index()];
        synchronized( this ) {
            if( !ss.isOpen() ) {
                return;
            }
            ss.close();
            updateDefaultStream();
        }
    }

    @Override
    public void seek( long micros ) throws JavException {
        Stream stream = mDefaultStream;
        if( stream == null ) {
            return;
        }

        Rational timeBase = stream.timeBase();
        long pts = stream.microsToPts( micros );
        long startPts = stream.javStream().startTime();

        mFormat.seek( stream.index(), pts, 0 );

        for( int i = 0; i < mStreams.length; i++ ) {
            Stream ss = mStreams[i];
            if( timeBase.equals( ss.timeBase() ) ) {
                ss.seekPts( pts - startPts + ss.javStream().startTime() );
            } else {
                ss.seekMicros( micros );
            }
        }

        mEof = false;
        mPacketValid = false;
        mFlushStream = 0;
    }

    @Override
    public Packet readNext() throws IOException {
        if( mEof ) {
            return flush();
        }

        // Check if we still have packet.
        if( !mPacketValid ) {
            int err = mFormat.readPacket( mPacket );
            if( err != 0 ) {
                if( err == Jav.AVERROR_EOF ) {
                    mEof = true;
                    return flush();
                } else {
                    throw new JavException( err );
                }
            }

            mPacketValid = true;
        }

        int idx = mPacket.streamIndex();
        if( idx < 0 || idx >= mStreams.length ) {
            mPacketValid = false;
            return null;
        }

        Packet ret = mStreams[idx].process( mPacket, false );
        mPacketValid = mPacket.size() > 0;
        return ret;
    }

    @Override
    public boolean isOpen() {
        return mDefaultStream != null;
    }

    @Override
    public void close() throws IOException {
        for( int i = 0; i < mStreams.length; i++ ) {
            try {
                Stream ss = mStreams[i];
                if( ss == null || !ss.isOpen() ) {
                    continue;
                }

                ss.close();
            } catch( Exception ex ) {}
        }

        updateDefaultStream();
    }


    public void overrideTimestamps( long mediaStartMicros ) {
        if( mFirstStreamIndex < 0 ) {
            return;
        }

        JavStream ss = mFormat.stream( mFirstStreamIndex );
        Rational firstBase = ss.timeBase();
        long firstPts = ss.startTime();

        for( Stream s : mStreams ) {
            Rational tb = s.timeBase();
            long startPts = firstPts;

            if( tb.equals( firstBase ) ) {
                startPts = firstPts;
            } else {
                startPts = firstPts * firstBase.num() * tb.den() / (firstBase.den() * tb.num());
            }

            s.initTimer( startPts, mediaStartMicros );
        }
    }


    public void useEmbeddedTimestamps( long offsetMicros ) {
        for( Stream s : mStreams ) {
            s.initTimer( 0, offsetMicros );
        }
    }


    private Packet flush() throws IOException {
        for( ; mFlushStream >= 0 && mFlushStream < mStreams.length; mFlushStream++ ) {
            Packet packet = mStreams[mFlushStream].process( mNullPacket, true );
            if( packet != null ) {
                return packet;
            }
        }

        mFlushStream = -1;
        throw new EOFException();
    }



    /**
     * Determines which stream should be used by default.
     */
    private synchronized void updateDefaultStream() {
        // Stream stream = null;
        // int score = 0;
        //
        // for(Stream ss: mStreams) {
        // if(ss == null || !ss.isOpen())
        // continue;
        //
        // int type = ss.javStream().codecContext().codecType();
        // int sc = 1;
        //
        // switch(type) {
        // case AVMEDIA_TYPE_VIDEO:
        // mDefaultStream = ss;
        // return;
        //
        // case AVMEDIA_TYPE_AUDIO:
        // sc = 2;
        // break;
        //
        // default:
        // sc = 1;
        // break;
        // }
        //
        // if(sc > score) {
        // stream = ss;
        // score = sc;
        // }
        // }
        //
        // mDefaultStream = stream;
    }



    private static abstract class Stream implements StreamHandle {

        final JavStream mStream;
        final Guid mGuid;
        final int mIndex;
        final int mType;
        final Rational mTimeBase;
        final PacketTimer mTimer;

        Stream( JavStream stream ) {
            mStream = stream;
            mGuid = Guid.create();
            mIndex = stream.index();
            mType = stream.codecContext().codecType();
            mTimeBase = stream.timeBase();
            mTimer = new PacketTimer( mTimeBase );
        }


        @Override
        public Guid guid() {
            return mGuid;
        }


        @Override
        public int type() {
            return mType;
        }


        @Override
        public PictureFormat pictureFormat() {
            return null;
        }


        @Override
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


        @SuppressWarnings( "unused" )
        public long ptsToMicros( long pts ) {
            return mTimer.ptsToMicros( pts );
        }


        public long microsToPts( long micros ) {
            return mTimer.microsToPts( micros );
        }


        @Override
        public int hashCode() {
            return mGuid.hashCode();
        }

        public abstract boolean isOpen();

        public abstract void close() throws IOException;

        abstract void open( int poolSize ) throws JavException;

        abstract Packet process( JavPacket packet, boolean flushing ) throws IOException;

        abstract void seekPts( long pts );

        abstract void seekMicros( long micros );

    }



    private static class VideoStream extends Stream {

        private final JavCodecContext mCodecContext;
        private final PictureFormat mPictureFormat;

        private final long[] mRange = new long[2];
        private final int[] mGotFrame = new int[1];

        private boolean mIsOpen = false;
        private HardPool<VideoPacket> mPool = null;
        private boolean mHasKeyFrame = false;
        private VideoPacket mCurrentFrame = null;


        public VideoStream( JavStream stream,
                            long startPts,
                            long startMicros )
        {
            super( stream );
            mCodecContext = stream.codecContext();
            mPictureFormat = PictureFormat.fromCodecContext( mCodecContext );
            initTimer( startPts, startMicros );
        }



        @Override
        public PictureFormat pictureFormat() {
            return mPictureFormat;
        }


        @Override
        public void open( int poolSize ) throws JavException {
            if( mIsOpen ) {
                return;
            }

            if( !mCodecContext.isOpen() ) {
                JavCodec codec = JavCodec.findDecoder( mCodecContext.codecId() );
                if( codec == null ) {
                    throw new JavException( Jav.AVERROR_DECODER_NOT_FOUND,
                                            "Codec not found for stream: " + mStream.index() );
                }
                mCodecContext.refcountedFrames( 1 );
                mCodecContext.open( codec );
            }

            mPool = new HardPool<VideoPacket>( poolSize );
            mIsOpen = true;
        }


        @Override
        public boolean isOpen() {
            return mIsOpen;
        }


        @Override
        public void close() {
            if( !mIsOpen ) {
                return;
            }

            mIsOpen = false;
            if( mCodecContext.isOpen() ) {
                try {
                    mCodecContext.close();
                } catch( Exception ex ) {}
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


        @Override
        public Packet process( JavPacket packet, boolean flushing ) throws IOException {
            if( !mIsOpen ) {
                if( !flushing ) {
                    mTimer.packetSkipped( packet.pts(), packet.duration(), mRange );
                    packet.moveDataPointer( packet.size() );
                }
                return null;
            }

            VideoPacket ret = mCurrentFrame;
            mCurrentFrame = null;

            if( ret == null ) {
                ret = mPool.poll();
                if( ret == null ) {
                    ret = VideoPacket.alloc( mPool );
                } else {
                    ret.freeData();
                }
            }

            int n = mCodecContext.decodeVideo( packet, ret, mGotFrame );
            if( n < 0 ) {
                throw new JavException( n );
            } else if( !flushing ) {
                packet.moveDataPointer( n );
            }

            if( mGotFrame[0] == 0 ) {
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


        @Override
        public void seekPts( long pts ) {
            mTimer.seekPts( pts );
            if( mIsOpen ) {
                mCodecContext.flushBuffers();
            }
        }


        @Override
        public void seekMicros( long micros ) {
            mTimer.seekMicros( micros );
            if( mIsOpen ) {
                mCodecContext.flushBuffers();
            }
        }

    }



    private static class AudioStream extends Stream {

        private final JavCodecContext mCodecContext;
        private final AudioFormat mFormat;
        
        private final long[] mRange = new long[2];
        private final int[] mGotFrame = new int[1];

        private boolean mIsOpen = false;
        private HardPool<AudioPacket> mPool = null;
        private AudioPacket mCurrentFrame = null;


        public AudioStream( JavStream stream,
                            long startPts,
                            long startMicros )
        {
            super( stream );

            mCodecContext = stream.codecContext();
            mFormat = AudioFormat.fromCodecContext( mCodecContext );
            
            initTimer( startPts, startMicros );
        }



        @Override
        public AudioFormat audioFormat() {
            return mFormat;
        }


        @Override
        public void open( int poolSize ) throws JavException {
            if( mIsOpen ) {
                return;
            }
            if( !mCodecContext.isOpen() ) {
                JavCodec codec = JavCodec.findDecoder( mCodecContext.codecId() );
                if( codec == null ) {
                    throw new JavException( AVERROR_DECODER_NOT_FOUND,
                                            "Codec not found for stream: " + mStream.index() );
                }
                mCodecContext.open( codec );
            }

            mPool = new HardPool<AudioPacket>( poolSize );
            mIsOpen = true;
        }


        @Override
        public boolean isOpen() {
            return mIsOpen;
        }


        @Override
        public void close() {
            mPool = null;
            if( mCodecContext.isOpen() ) {
                try {
                    mCodecContext.close();
                } catch( Exception ex ) {}
            }
        }


        @Override
        public AudioPacket process( JavPacket packet, boolean flushing ) throws IOException {
            if( mPool == null ) {
                if( !flushing ) {
                    mTimer.packetSkipped( packet.pts(), packet.duration(), mRange );
                    packet.moveDataPointer( packet.size() );
                }
                return null;
            }

            AudioPacket ret = mCurrentFrame;
            mCurrentFrame = null;

            if( ret == null ) {
                ret = mPool.poll();
                if( ret == null ) {
                    ret = AudioPacket.newAutoInstance( mPool );
                }
            }

            int n = mCodecContext.decodeAudio( packet, ret, mGotFrame );
            if( n < 0 ) {
                throw new JavException( n );
            } else if( !flushing ) {
                packet.moveDataPointer( n );
            }
            if( mGotFrame[0] == 0 ) {
                mCurrentFrame = ret;
                return null;
            }

            mTimer.packetDecoded( ret.bestEffortTimestamp(), ret.packetDuration(), mRange );
            ret.init( this, mFormat, mRange[0], mRange[1] );
            return ret;
        }


        @Override
        public void seekPts( long pts ) {
            mTimer.seekPts( pts );
            if( mIsOpen ) {
                mCodecContext.flushBuffers();
            }
        }


        @Override
        public void seekMicros( long micros ) {
            mTimer.seekMicros( micros );
            if( mIsOpen ) {
                mCodecContext.flushBuffers();
            }
        }

    }



    private static class NullStream extends Stream {

        private final long[] mRange = new long[2];


        public NullStream( JavStream stream ) {
            super( stream );
        }



        @Override
        public boolean isOpen() {
            return false;
        }


        @Override
        public void open( int poolSize ) throws JavException {
            throw new JavException( "Cannot open stream of type: " + mType );
        }


        @Override
        public void close() {}


        @Override
        public Packet process( JavPacket packet, boolean flushing ) throws IOException {
            if( flushing ) {
                return null;
            }

            long pts = packet.pts();
            long duration = packet.duration();
            mTimer.packetSkipped( pts, duration, mRange );
            packet.moveDataPointer( packet.size() );

            return null;
        }


        @Override
        public void seekPts( long pts ) {
            mTimer.seekPts( pts );
        }


        @Override
        public void seekMicros( long micros ) {
            mTimer.seekMicros( micros );
        }

    }

}
