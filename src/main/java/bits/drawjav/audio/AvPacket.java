///*
// * Copyright (c) 2014. Massachusetts Institute of Technology
// * Released under the BSD 2-Clause License
// * http://opensource.org/licenses/BSD-2-Clause
// */
//
//package bits.drawjav.audio;
//
//import java.nio.*;
//
//import bits.drawjav.Packet;
//import bits.drawjav.StreamHandle;
//import bits.jav.Jav;
//import bits.jav.JavException;
//import bits.jav.codec.JavFrame;
//import bits.jav.util.JavSampleFormat;
//import bits.util.ref.*;
//
//
//
///**
// * @author decamp
// */
//public class AvPacket extends JavFrame implements Packet {
//
//
//    public static AvPacket createAuto( ObjectPool<? super AvPacket> optPool ) {
//        long p = nAllocFrame();
//        if( p == 0 ) {
//            throw new OutOfMemoryError("Allocation failed.");
//        }
//        return new AvPacket( p, optPool );
//    }
//
//
//    public static AvPacket createAudio( ObjectPool<? super AvPacket> optPool,
//                                           AudioFormat format,
//                                           int samplesPerChannel,
//                                           int align )
//    {
//        assert samplesPerChannel >= 0;
//
//        int size = JavSampleFormat.getBufferSize( format.channels(),
//                                                  samplesPerChannel,
//                                                  format.sampleFormat(),
//                                                  align,
//                                                  null );
//
//        if( size < 0 ) {
//            throw new RuntimeException( new JavException( size ) );
//        }
//
//        ByteBuffer buf = ByteBuffer.allocateDirect( size );
//        buf.order( ByteOrder.nativeOrder() );
//        return createFilled( optPool, format, samplesPerChannel, align, buf );
//    }
//
//
//    public static AvPacket createFilled( ObjectPool<? super AvPacket> optPool,
//                                            AudioFormat format,
//                                            int samplesPerChannel,
//                                            int align,
//                                            ByteBuffer buf )
//    {
//        long pointer = nAllocFrame();
//        if( pointer == 0 ) {
//            throw new OutOfMemoryError();
//        }
//
//        AvPacket ret = new AvPacket( pointer, optPool );
//        ret.fillAudioFrame( format.channels(),
//                            samplesPerChannel,
//                            format.sampleFormat(),
//                            buf,
//                            align );
//        ret.mFormat = format;
//        return ret;
//    }
//
//
//    private StreamHandle mStream;
//    private AudioFormat  mFormat;
//    private long         mStartMicros;
//    private long         mStopMicros;
//    private boolean      mIsGap;
//
//    @SuppressWarnings({ "unchecked", "rawtypes" })
//    public AvPacket( long pointer, ObjectPool<? super AvPacket> optPool ) {
//        super( pointer, (ObjectPool)optPool );
//    }
//
//
//    @Override
//    public StreamHandle stream() {
//        return mStream;
//    }
//
//
//    public void stream( StreamHandle stream ) {
//        mStream = stream;
//    }
//
//
//    public int type() {
//        return mStream == null ? Jav.AVMEDIA_TYPE_UNKNOWN : mStream.type();
//    }
//
//    @Override
//    public long startMicros() {
//        return mStartMicros;
//    }
//
//
//    public void startMicros( long startMicros ) {
//        mStartMicros = startMicros;
//    }
//
//    @Override
//    public long stopMicros() {
//        return mStopMicros;
//    }
//
//
//    public void stopMicros( long stopMicros ) {
//        mStopMicros  = stopMicros;
//    }
//
//
//    public boolean isGap() {
//        return mIsGap;
//    }
//
//
//    public void isGap( boolean gap ) {
//        mIsGap = gap;
//    }
//
//
//    public AudioFormat toAudioFormat() {
//        return new AudioFormat( channels(), sampleRate(), format(), channelLayout() );
//    }
//
//
//    public void setAudioFormat( AudioFormat format ) {
//        sampleRate( format.sampleRate() );
//        channels( format.channels() );
//        format( format.sampleFormat() );
//        channelLayout( format.channelLayout() );
//    }
//
//    /**
//     * Initializes packet object.
//     */
//    public void init( StreamHandle stream,
//                      long startMicros,
//                      long stopMicros,
//                      AudioFormat format,
//                      boolean isGap )
//    {
//        mStream      = stream;
//        mStartMicros = startMicros;
//        mStopMicros  = stopMicros;
//        mIsGap       = isGap;
//        if( format != null ) {
//            setAudioFormat( format );
//        }
//    }
//
//}
