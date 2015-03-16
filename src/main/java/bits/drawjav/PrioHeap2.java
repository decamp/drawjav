/*
 * Copyright (c) 2014. Massachusetts Institute of Technology
 * Released under the BSD 2-Clause License
 * http://opensource.org/licenses/BSD-2-Clause
 */

package bits.drawjav;

import java.util.*;


/**
 * Intrinsic priority queue collection. This is "intrinsic" in that it only operates on HeapNode objects,
 * which means this collection does not need to create a node object to wrap each object and can
 * work with zero memory allocation. The downside is that if you add an item to two different heaps,
 * you'll have massive problems.
 * 
 * @author decamp
 */
@SuppressWarnings( {"unchecked", "rawtypes"} )
public class PrioHeap2<T extends HeapNode> extends HeapNode {

    private final Comparator mComp;
    public LinkedList<T> mArr;

    public PrioHeap2() {
        this( null );
    }


    public PrioHeap2( Comparator<? super T> comp ) {
        mComp = comp;
        mArr = new LinkedList<T>();
    }
     
    
    
    public void offer( T node ) {
        mArr.offer( node );
    }

    
    
    public T peek() {
        return mArr.peek();
    }
    
    
    public T poll() {
        return mArr.poll();
    }
    
    
    public T remove() {
        return mArr.remove();
    }
    
    
    public T remove( int idx ) {
        return mArr.remove( idx );
    }
    
    
    public boolean remove( T node ) {
        return mArr.remove( node );
    }
    
    
    public void reschedule( T node ) {}

    
    public T get( int idx ) {
        return mArr.get( idx );
    }


    public void clear() {
        mArr.clear();
    }


    public int size() {
        return mArr.size();
    }


    public boolean isEmpty() {
        return mArr.isEmpty();
    }

}
