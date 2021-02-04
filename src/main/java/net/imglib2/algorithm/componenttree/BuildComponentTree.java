/*
 * #%L
 * ImgLib2: a general-purpose, multidimensional image processing library.
 * %%
 * Copyright (C) 2009 - 2016 Tobias Pietzsch, Stephan Preibisch, Stephan Saalfeld,
 * John Bogovic, Albert Cardona, Barry DeZonia, Christian Dietz, Jan Funke,
 * Aivar Grislis, Jonathan Hale, Grant Harris, Stefan Helfrich, Mark Hiner,
 * Martin Horn, Steffen Jaensch, Lee Kamentsky, Larry Lindsey, Melissa Linkert,
 * Mark Longair, Brian Northan, Nick Perry, Curtis Rueden, Johannes Schindelin,
 * Jean-Yves Tinevez and Michael Zinsmaier.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package net.imglib2.algorithm.componenttree;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Comparator;
import java.util.PriorityQueue;

import ij.IJ;
import net.imglib2.Localizable;
import net.imglib2.Point;
import net.imglib2.Positionable;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.componenttree.mser.MserPartialComponent;
import net.imglib2.algorithm.componenttree.pixellist.PixelListComponent;
import net.imglib2.algorithm.componenttree.pixellist.PixelListComponentTree;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.Type;
import net.imglib2.type.logic.BitType;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.UnsignedByteType;

/**
 * Build the component tree of an image. This is an implementation of the
 * algorithm described by D. Nister and H. Stewenius in "Linear Time Maximally
 * Stable Extremal Regions" (ECCV 2008).
 * <p>
 * The input to the algorithm is a {@code RandomAccessibleInterval< T >}.
 * Further, a {@code Comparator<T>} and a {@link PartialComponent.Generator} to
 * instantiate new components are required. Pixel locations are aggregated in
 * {@link PartialComponent}s which are passed to a
 * {@link PartialComponent.Handler} whenever a connected component for a
 * specific threshold is completed.
 * </p>
 * <p>
 * Building up a tree structure out of the completed components should happen in
 * the {@link PartialComponent.Handler} implementation. See
 * {@link PixelListComponentTree} for an example.
 * </p>
 * <p>
 * <strong>TODO</strong> Add support for non-zero-min RandomAccessibleIntervals.
 * (Currently, we assume that the input image is a <em>zero-min</em> interval.)
 * </p>
 * 
 * @param <T> value type of the input image.
 * @param <C> component type.
 * 
 * @author Tobias Pietzsch
 */
public final class BuildComponentTree<T extends Type<T>, C extends PartialComponent<T, C>> {
	/**
	 * Run the algorithm. Completed components are emitted to the
	 * {@link PartialComponent.Handler} which is responsible for building up the
	 * tree structure. An implementations of {@link PartialComponent.Handler} is
	 * provided for example by {@link PixelListComponentTree}.
	 * 
	 * @param input              input image.
	 * @param componentGenerator provides new {@link PartialComponent} instances.
	 * @param componentHandler   receives completed {@link PartialComponent}s.
	 * @param comparator         determines ordering of threshold values.
	 */
	public static <T extends Type<T>, C extends PartialComponent<T, C>> void buildComponentTree(
			final RandomAccessibleInterval<T> input, 
			final PartialComponent.Generator<T, C> componentGenerator,
			final PartialComponent.Handler<C> componentHandler, 
			final Comparator<T> comparator) {
		new BuildComponentTree<T, C>(input, componentGenerator, componentHandler, comparator);
	}

	/**
	 * Run the algorithm. Completed components are emitted to the
	 * {@link PartialComponent.Handler} which is responsible for building up the
	 * tree structure. An implementations of {@link PartialComponent.Handler} is
	 * provided for example by {@link PixelListComponentTree}.
	 * 
	 * @param input              input image of a comparable value type.
	 * @param componentGenerator provides new {@link PartialComponent} instances.
	 * @param componentHandler   receives completed {@link PartialComponent}s.
	 * @param darkToBright       determines ordering of threshold values. If it is
	 *                           true, then thresholds are applied from low to high
	 *                           values. Note that the
	 *                           {@link PartialComponent.Generator#createMaxComponent()}
	 *                           needs to match this ordering. For example when
	 *                           IntType using darkToBright=false, then
	 *                           {@link PartialComponent.Generator#createMaxComponent()}
	 *                           should provide a Integer.MIN_VALUE valued
	 *                           component.
	 */
	public static <T extends Type<T> & Comparable<T>, C extends PartialComponent<T, C>> void buildComponentTree(
			final RandomAccessibleInterval<T> input, final PartialComponent.Generator<T, C> componentGenerator,
			final PartialComponent.Handler<C> componentHandler, final boolean darkToBright) {
		new BuildComponentTree<T, C>(input, componentGenerator, componentHandler,
				darkToBright ? new DarkToBright<T>() : new BrightToDark<T>());
	}

	/**
	 * Default comparator for {@link Comparable} pixel values for dark-to-bright
	 * pass.
	 */
	public static final class DarkToBright<T extends Comparable<T>> implements Comparator<T> {
		@Override
		public int compare(final T o1, final T o2) {
			return o1.compareTo(o2);
		}
	}

	/**
	 * Default comparator for {@link Comparable} pixel values for bright-to-dark
	 * pass.
	 */
	public static final class BrightToDark<T extends Comparable<T>> implements Comparator<T> {
		@Override
		public int compare(final T o1, final T o2) {
			return o2.compareTo(o1);
		}
	}

	/**
	 * Iterate pixel positions in 4-neighborhood.
	 */
	private static final class Neighborhood {
		/**
		 * index of the next neighbor to visit. 0 is pixel at x-1, 1 is pixel at x+1, 2
		 * is pixel at y-1, 3 is pixel at y+1, and so on.
		 */
		private int n;

		/**
		 * number of neighbors, e.g., 4 for 2d images.
		 */
		private final int nBound;

		/**
		 * image dimensions. used to check out-of-bounds.
		 */
		final long[] dimensions;

		public Neighborhood(final long[] dim) {
			n = 0;
			nBound = dim.length * 2;
			dimensions = dim;
		}

		public int getNextNeighborIndex() {
			return n;
		}

		public void setNextNeighborIndex(final int n) {
			this.n = n;
		}

		public void reset() {
			n = 0;
		}

		public boolean hasNext() {
			return n < nBound;
		}

		/**
		 * Set neighbor to the next (according to
		 * {@link BuildComponentTree.Neighborhood#n}) neighbor position of current.
		 * Assumes that prior to any call to next() neighbor was a the same position as
		 * current, i.e. neighbor position is only modified incrementally.
		 * 
		 * @param currentPos
		 * @param neighborPos
		 * @return false if the neighbor position is out of bounds, true otherwise.
		 */
		public boolean next(final Localizable currentPos, final Positionable neighborPos, final Positionable visitedPos) {
			final int d = n / 2;
			final boolean bck = (n == 2 * d); // n % 2 == 0
			++n;
			if (bck) {
				if (d > 0) {
					neighborPos.setPosition(currentPos.getLongPosition(d - 1), d - 1);
					visitedPos.setPosition(currentPos.getLongPosition(d - 1), d - 1);
				}
				final long dpos = currentPos.getLongPosition(d) - 1;
				neighborPos.setPosition(dpos, d);
				visitedPos.setPosition(dpos, d);
				return dpos >= 0;
			} else {
				final long dpos = currentPos.getLongPosition(d) + 1;
				neighborPos.setPosition(dpos, d);
				visitedPos.setPosition(dpos, d);
				return dpos < dimensions[d];
			}
		}
	}

	/**
	 * A pixel position on the heap of boundary pixels to be processed next. The
	 * heap is sorted by pixel values.
	 */
	private final class BoundaryPixel extends Point implements Comparable<BoundaryPixel> {
		private final T value;

		// TODO: this should be some kind of iterator over the neighborhood
		private int nextNeighborIndex;

		public BoundaryPixel(final Localizable position, final T value, final int nextNeighborIndex) {
			super(position);
			this.nextNeighborIndex = nextNeighborIndex;
			this.value = value.copy();
		}

		public int getNextNeighborIndex() {
			return nextNeighborIndex;
		}

		public T get() {
			return value;
		}

		@Override
		public int compareTo(final BoundaryPixel o) {
			return comparator.compare(value, o.value);
		}
	}

	private final ArrayDeque<BoundaryPixel> reusableBoundaryPixels;

	private BoundaryPixel createBoundaryPixel(final Localizable position, final T value, final int nextNeighborIndex) {
		if (reusableBoundaryPixels.isEmpty())
			return new BoundaryPixel(position, value, nextNeighborIndex);
		else {
			final BoundaryPixel p = reusableBoundaryPixels.pop();
			p.setPosition(position);
			p.value.set(value);
			p.nextNeighborIndex = nextNeighborIndex;
			return p;
		}
	}

	private void freeBoundaryPixel(final BoundaryPixel p) {
		reusableBoundaryPixels.push(p);
	}

	private final PartialComponent.Generator<T, C> componentGenerator;
	private final PartialComponent.Handler<C> componentOutput;
	private final Neighborhood neighborhood;
	private final RandomAccessible<BitType> visited;
	private final RandomAccess<BitType> visitedPos;
	private final PriorityQueue<BoundaryPixel> boundaryPixels;
	private final ArrayDeque<C> componentStack;
	private final Comparator<T> comparator;
	
	private final RandomAccessibleInterval<T> input;	// wilbur

	/**
	 * Set up data structures and run the algorithm. Completed components are
	 * emitted to the provided {@link PartialComponent.Handler}.
	 * 
	 * @param input              input image.
	 * @param componentGenerator provides new {@link PartialComponent} instances.
	 * @param componentOutput    receives completed {@link PartialComponent}s.
	 * @param comparator         determines ordering of threshold values.
	 */
	private BuildComponentTree(final RandomAccessibleInterval<T> input,
			final PartialComponent.Generator<T, C> componentGenerator,
			final PartialComponent.Handler<C> componentOutput, 
			final Comparator<T> comparator) {
		
		this.input = input;		// wilbur

		reusableBoundaryPixels = new ArrayDeque<BoundaryPixel>();
		this.componentGenerator = componentGenerator;
		this.componentOutput = componentOutput;

		final long[] dimensions = new long[input.numDimensions()];
		input.dimensions(dimensions);

		final ImgFactory<BitType> imgFactory = new ArrayImgFactory<>(new BitType());
		
		visited = imgFactory.create(dimensions);				// creates the visited (bit) map
		visitedPos = visited.randomAccess();

		neighborhood = new Neighborhood(dimensions);
		boundaryPixels = new PriorityQueue<BoundaryPixel>();

		componentStack = new ArrayDeque<C>();
		componentStack.push(componentGenerator.createMaxComponent());	// push empty component with value 255
		
//		componentStack.peek().setValue((T) (new UnsignedByteType(256))); // wilbur: this does NOT work, BUG!!
//		componentStack.peek().setValue((T) (new UnsignedByteType(255))); // wilbur

		IJ.log("initial component stack: " + componentStack.peek().getValue());
		
		this.comparator = comparator;

		//foo(input);
		run(input);
	}
	

	/**
	 * Main loop of the algorithm. This follows exactly along steps of the algorithm
	 * as described in the paper.
	 * 
	 * @param input the input image.
	 */
	private void run(final RandomAccessibleInterval<T> input) {	
		System.out.println("**** running ****");
		final RandomAccess<T> currentPos = input.randomAccess();	// current position
		final RandomAccess<T> neighborPos = input.randomAccess();	// neighbor position
		
		//IJ.log("boundaryPixels.size() = " + boundaryPixels.size());
		boundaryPixels.clear();

		input.min(currentPos);						// sets current position to (0,0)
		neighborPos.setPosition(currentPos);		// neighbor = current
		visitedPos.setPosition(currentPos);			// visitedRandomAccess = current

		final T currentLevel = currentPos.get().createVariable();
		final T neighborLevel = currentPos.get().createVariable();

		// Note that step numbers in the comments below refer to steps in the
		// Nister & Stewenius paper.

		// step 2
		visitedPos.get().set(true);	// mark start pixel as visited
		currentLevel.set(currentPos.get());		// currentLevel = I(currentPos)

		// step 3
		componentStack.push(componentGenerator.createComponent(currentLevel));	// push initial component

		// step 4
		listComponentStack();
		boolean done = false;
		while (!done) {
			while (neighborhood.hasNext()) {
//				if (!neighborhood.next(current, neighbor, visitedRandomAccess)) {
//					continue;
//				}
				if (neighborhood.next(currentPos, neighborPos, visitedPos) && !visitedPos.get().get()) {
				//if (!visitedRandomAccess.get().get()) {
					// actually we could visit( neighbor ); here.
					// however, because wasVisited() already set the
					// visitedPos to the correct position, this is faster:
					visitedPos.get().set(true);			// set visited true
					neighborLevel.set(neighborPos.get());		// neighborLevel = I(neighbor)
					
					//if (comparator.compare(neighborLevel, currentLevel) >= 0) {
					if (getIntValue(neighborLevel) >= getIntValue(currentLevel)) {
						boundaryPixels.add(createBoundaryPixel(neighborPos, neighborLevel, 0));	// push neighbor (0 = none of its neighbors has been processed!) 
					} else {
						boundaryPixels.add(createBoundaryPixel(currentPos, currentLevel, neighborhood.getNextNeighborIndex()));
						currentPos.setPosition(neighborPos);		// current = neighbor
						currentLevel.set(neighborLevel);	// currentLevel = I(neighbor)
						// go to 3, i.e.:
						componentStack.push(componentGenerator.createComponent(currentLevel));
						neighborhood.reset();
					}
				}
			}

			// step 5
			C component = componentStack.peek();
			component.addPosition(currentPos);		// ad pixel

			// step 6
			if (boundaryPixels.isEmpty()) {
				processStack(currentLevel);
				done = true; //break; //return;
			}
			else {
				BoundaryPixel p = boundaryPixels.poll();
				//if (comparator.compare(p.get(), currentLevel) != 0) {
				if (getIntValue(p.get()) !=  getIntValue(currentLevel)) {
					// step 7
					processStack(p.get());
				}
				currentPos.setPosition(p);						// current <- nextBoundaryPosition
				neighborPos.setPosition(currentPos);				// neighbor <- current
				visitedPos.setPosition(currentPos);	// visit current
				currentLevel.set(p.get());
				neighborhood.setNextNeighborIndex(p.getNextNeighborIndex());
				freeBoundaryPixel(p);
			}
		}
		
		validateAllVisited(input);	// wilbur
	}
	
	private int processStackCtr = 0;

	/**
	 * This is called whenever the current value is raised.
	 * @param newLevel
	 */
	private void processStack(final T newLevel) {
		
		IJ.log((processStackCtr++) + " processStack(): newLevel=" + newLevel + " componentStack = " + listComponentStack());
		if (getIntValue(newLevel) == 0 || getIntValue(newLevel) == 255) {
			IJ.log("************************************************");
		}
		boolean done = false;
		while (!done) {
			// process component on top of stack
			C component1 = componentStack.pop();
			
			IJ.log("   +++ emitting component " + ((MserPartialComponent<T>)component1).ID 
					+ " level=" + component1.getValue() 
					+ " size=" + ((MserPartialComponent<T>)component1).size() 
					+ " levels=" + levelsToString(component1)
					);
			//IJ.log("   +++ children = " + childrenToString(component1));
			
			componentOutput.emit(component1);

			// get level of second component on stack
			C component2 = componentStack.peek();
			if (component2 == null) {
				throw new RuntimeException("empty stack - something odd happened");
			}

			//final int c = comparator.compare(value, secondComponent.getValue());
			int level1 = getIntValue(newLevel);
			int level2 = getIntValue(component2.getValue());
			//if (c < 0) {
			if (level1 < level2) {
				component1.setValue(newLevel);
				componentStack.push(component1);	// component1 back on the stack
				done = true;
			} 
			else {
				IJ.log(String.format("   *** processStack(): merging components %s <- %s", 
						component2.getValue().toString() , component1.getValue().toString()));
				component2.merge(component1);
				if (level1 == level2) { //if (c > 0)
					done = true;
				}
			}
		}
		//IJ.log("           END  newLevel=" + newLevel + " topStackLevel=" + componentStack.peek().getValue());
	}
	
	
	// ---------------- Wilbur testing -----------------------------------------------
	
	int getIntValue(T x) {
		IntegerType<?> it = (IntegerType<?>) x;
		return it.getInteger();
		//return new Integer(x.toString());
	}
	
	private void foo(final RandomAccessibleInterval<T> input) {
		System.out.println("**** foo ****");
		final RandomAccess<T> current = input.randomAccess();	// current position
		final RandomAccess<T> neighbor = input.randomAccess();	// neighbor position
		
		current.setPositionAndGet(3, 7);
		neighbor.setPosition(current);
		
		System.out.println("current = " + current.positionAsPoint().toString());
		System.out.println("neighbor = " + neighbor.positionAsPoint().toString());
		while (neighborhood.hasNext()) {
			boolean in = neighborhood.next(current, neighbor, visitedPos);
			//System.out.println("   " + in + " current = " + current.positionAsPoint().toString());
			System.out.println("   " + in + " neighbor = " + neighbor.positionAsPoint().toString());
		}
	}
	
	private String listComponentStack() {
		int N = componentStack.size();
		int[] levels = new int[N];
		int[] sizes = new int[N];
		int[] ids = new int[N];
		
		int i = 0;
		for (C component : componentStack) {
			MserPartialComponent< T > mser = (MserPartialComponent< T >) component;
			levels[i] = getIntValue(mser.getValue());
			sizes[i] = (int) mser.size();
			ids[i] = mser.ID;
			i++;
		}
		return "compStack=" + Arrays.toString(ids) + " levels="+Arrays.toString(levels) + " sizes="+Arrays.toString(sizes);
	}
	
	private void validateAllVisited(final RandomAccessibleInterval<T> input) {
		// wilbur: check if all positions were visited
		final long[] dimensions = new long[input.numDimensions()];
		input.dimensions(dimensions);
		int width = (int) dimensions[0];
		int height = (int) dimensions[1];
		visitedPos.setPosition(new int[] {0,0});
		int cnt = 0;
		for (int u = 0; u < width; u++) {
			for (int v = 0; v < height; v++) {
				if (!visitedPos.setPositionAndGet(u, v).get()) {
					cnt++;
				}
			}
		}
		IJ.log("non-visited positions: " + cnt);
	}
	
	private String levelsToString(C component) {
		MserPartialComponent< T > mser = (MserPartialComponent< T >) component;
		int[] levels = new int[(int)mser.size()];
		int i = 0;
		for (Localizable pos : mser.pixelList) {
//			int x = pos.getIntPosition(0);
//			int y = pos.getIntPosition(1);
			levels[i] = getIntValue(input.getAt(pos));
			i++;
		}
		Arrays.sort(levels);
		return Arrays.toString(levels);
	}
	
	private String childrenToString(C component) {
		MserPartialComponent< T > mser = (MserPartialComponent< T >) component;
		int[] chLevels = new int[mser.children.size()];
		int i = 0;
		for (MserPartialComponent< T >  ch : mser.children) {
			chLevels[i] = getIntValue(ch.getValue());
		}
		Arrays.sort(chLevels);
		return Arrays.toString(chLevels);
	}

	

}
