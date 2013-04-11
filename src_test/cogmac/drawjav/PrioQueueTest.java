package cogmac.drawjav;

import java.util.*;

import org.junit.Test;

import cogmac.drawjav.*;
import static org.junit.Assert.*;


public class PrioQueueTest {
    

    @Test
    public void testRemoveHead() {
        PrioQueue<Node> q = new PrioQueue<Node>( NODE_COMP );
        List<Node> nodes = nodeList( 10 );
        
        for( Node n: nodes ) {
            q.offer( n );
        }
        for( int i = 0; i < nodes.size(); i++ ) {
            q.removeHead();
            assertEquals( q.size(), nodes.size() - i - 1 );
        }
    }
    
    
    @Test
    public void testRemoveTail() {
        PrioQueue<Node> q = new PrioQueue<Node>( NODE_COMP );
        List<Node> nodes = nodeList( 10 );
        
        for( Node n: nodes ) {
            q.offer( n );
        }
        for( int i = 0; i < nodes.size(); i++ ) {
            q.removeTail();
            assertEquals( q.size(), nodes.size() - i - 1 );
        }
    }
    
    
    @Test
    public void testQueue() {
        PrioQueue<Node> q = new PrioQueue<Node>( NODE_COMP );
        List<Node> nodes = nodeList( 10 );
        
        for( Node n: nodes ) {
            q.offer( n );
        }
        check( q, 10 );
        q.remove( nodes.get( 0 ) );
        check( q, 9 );
        q.remove( nodes.get( nodes.size() - 1 ) );
        check( q, 8 );
        
        for( Node n: nodes ) {
            q.remove( n );
        }
        check( q, 0 );
        
        for( Node n: nodes ) {
            q.offer( n );
        }
        check( q, 10 );
        
        for( int i = nodes.size() - 1; i >= 0; i-- ) {
            q.remove( nodes.get( i ) );
        }
        check( q, 0 );
        
        for( int i = 0; i < 25; i++ ) {
            Collections.shuffle( nodes );
            for( Node n: nodes ) {
                int size = q.mSize;
                q.offer( n );
                assertEquals( q.mSize, size + 1 );
            }
            if( i == 0 ) {
                check( q, 10 );
            }
            Collections.shuffle( nodes );
            for( Node n: nodes ) {
                int size = q.mSize;
                q.remove( n );
                assertEquals( q.mSize, size - 1 );
            }
            if( i == 0 ) {
                check( q, 0 );
            }
        }
    }
    


    static List<Node> nodeList( int len ) {
        List<Node> ret = new ArrayList<Node>( len );
        for( int i = 0; i < len; i++ ) {
            ret.add( new Node( ( i + 1 ) / 2 ) );
        }
        return ret;
    }
    
    
    static void check( PrioQueue<Node> q, int size ) {
        print( q );
        assertEquals( q.mSize, size );
    }

    
    static void print( PrioQueue<Node> q ) {
        Node n = q.mHead;
        System.out.print( "[ " );
        while( n != null ) {
            System.out.print( n.mValue );
            System.out.print( "  " );
            n = (Node)n.mNext;
        }
        System.out.println( " ]" );
    }

    
    static class Node extends DoubleLinkedNode {
        int mValue;
        
        public Node( int value ) {
            mValue = value;
        }
        
        
        public void clear() {}
            
        
    }

    
    static final Comparator<Node> NODE_COMP = new Comparator<Node>() {
        public int compare( Node a, Node b ) {
            return a.mValue - b.mValue;
        }
    };
    
}
