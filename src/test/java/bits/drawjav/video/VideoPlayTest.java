///*
// * Copyright (c) 2014. Massachusetts Institute of Technology
// * Released under the BSD 2-Clause License
// * http://opensource.org/licenses/BSD-2-Clause
// */
//
//package bits.drawjav.video;
//
//import java.io.File;
//import javax.swing.JFrame;
//
//import bits.drawjav.*;
//import bits.jav.Jav;
//import bits.microtime.Frac;
//import bits.microtime.PlayController;
//
//
///**
// * @author Philip DeCamp
// */
//public class VideoPlayTest {
//
//    public static void main( String[] args ) throws Exception {
//        test1();
//    }
//
//
//    static void test1() throws Exception {
//        JFrame frame = new JFrame();
//        frame.setSize( 800, 600 );
//        frame.setLocationRelativeTo( null );
//
//        OldVideoPanel panel = new OldVideoPanel();
//        frame.setContentPane( panel );
//        frame.setVisible( true );
//
////        File file = new File( "/code/bits/cannesamp/resources_ext/params_drwho/Dr Who Tweet Clip.mp4" );
//        File file = new File( "/code/bluefin/cannesamp/resources_ext/params_drwho/Doctor Who Primary Video.mp4" );
//        //File file = new File( "/code/bits/cannesamp/resources_ext/params_nfl/mcdonalds_preroll.mp4" );
//
////        {
////            PlayController playCont = PlayController.createEmpty();
////            FormatDecoder decoder = FormatDecoder.openFile( file, true, 0L );
////            decoder.openStream( decoder.stream( Jav.AVMEDIA_TYPE_VIDEO, 0 ) );
////
////            List<Packet> list = new ArrayList<Packet>();
////            while( true ) {
////                Packet p = decoder.readNext();
////                if( p != null ) {
////                    list.add( p );
////                    System.out.println( list.size() );
////                    if( list.size() > 64 ) {
////                        break;
////                    }
////                }
////            }
////        }
//
//        {
//            PlayController playCont = PlayController.createEmpty();
//            FormatReader decoder = FormatReader.openFile( file, true, 0L, null );
//            decoder.openStream( decoder.stream( Jav.AVMEDIA_TYPE_VIDEO, 0 ) );
//
//            StreamFormat fmt = StreamFormat.create( -1, -1, Jav.AV_PIX_FMT_BGR24, null );
//            RealtimeDriver driver = new RealtimeDriver( playCont.clock(), decoder, null, null );
//            driver.seekWarmupMicros( 3000000L );
//            driver.openVideoStream( null, decoder.stream( Jav.AVMEDIA_TYPE_VIDEO, 0 ), fmt, panel );
////                                    new PictureFormat( -1, -1, Jav.AV_PIX_FMT_BGRA, new bits.jav.util.Rational( 1, 1 ) ), panel );
//
//            playCont.control().clockRate( new Frac( 1, 1 ) );
//            playCont.control().clockSeek( 100000L );
//            driver.startThreadedMode();
//            playCont.control().clockStart();
//        }
//
//        Thread.sleep( 500L );
//        System.gc();
//        Thread.sleep( 500L );
//        System.out.println( "DONE" );
//
//
//
////        new DumbDriver( decoder, panel );
//    }
//
//}
