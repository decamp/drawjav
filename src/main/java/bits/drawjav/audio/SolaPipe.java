///*
// * Copyright (c) 2015. Massachusetts Institute of Technology
// * Released under the BSD 2-Clause License
// * http://opensource.org/licenses/BSD-2-Clause
// */
//
//package bits.drawjav.audio;
//
//import bits.drawjav.Pipe;
//import bits.microtime.Frac;
//import bits.microtime.SyncClockControl;
//
//import java.io.IOException;
//import java.util.List;
//
//
///**
// * @author Philip DeCamp
// */
//public class SolaPipe implements Pipe<AudioPacket>, SyncClockControl {
//
//    private final int mFreq;
//    private final AudioAllocator mAlloc;
//    private final SolaFilter mFilter;
//
//    private AudioPacket mDest = null;
//
//
//    public SolaPipe( int freq, AudioAllocator optAlloc ) {
//        mFreq = freq;
//        if( optAlloc != null ) {
//            mAlloc = optAlloc;
//        } else {
//            mAlloc = new OneStreamAudioAllocator( 32, -1, 1204 * 4 );
//        }
//        mFilter = new SolaFilter( freq );
//    }
//
//
//    @Override
//    public Result process( AudioPacket packet, List<? super AudioPacket> out ) throws IOException {
//        return Result.DONE;
//    }
//
//    @Override
//    public void clear() {
//        mFilter.clear();
//    }
//
//    @Override
//    public boolean isOpen() {
//        return false;
//    }
//
//    @Override
//    public void close() throws IOException {
//
//    }
//
//
//    @Override
//    public void clockStart( long execMicros ) {
//
//    }
//
//    @Override
//    public void clockStop( long execMicros ) {
//
//    }
//
//    @Override
//    public void clockSeek( long execMicros, long seekMicros ) {
//
//    }
//
//    @Override
//    public void clockRate( long execMicros, Frac rate ) {
//
//    }
//
//}
