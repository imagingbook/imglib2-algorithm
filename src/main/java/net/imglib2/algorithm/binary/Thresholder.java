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
package net.imglib2.algorithm.binary;

import java.util.Vector;

import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.converter.Converter;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.multithreading.Chunk;
import net.imglib2.multithreading.SimpleMultiThreading;
import net.imglib2.type.Type;
import net.imglib2.type.logic.BitType;

/**
 * Collection of static utilities meant to generate {@link BitType} images from
 * {@link Comparable} images.
 *
 * @author Jean-Yves Tinevez
 */
public class Thresholder
{

	/**
	 * Returns a new boolean {@link Img} generated by thresholding the values of
	 * the source image.
	 *
	 * @param source
	 *            the image to threshold.
	 * @param threshold
	 *            the threshold.
	 * @param above
	 *            if {@code true}, the target value will be true for source
	 *            values above the threshold, {@code false} otherwise.
	 * @param numThreads
	 *            the number of threads to use for thresholding.
	 * @return a new {@link Img} of type {@link BitType} and of same dimension
	 *         that the source image.
	 */
	public static final < T extends Type< T > & Comparable< T >> Img< BitType > threshold( final Img< T > source, final T threshold, final boolean above, final int numThreads )
	{
		final ImgFactory< T > factory = source.factory();
		try
		{
			final ImgFactory< BitType > bitFactory = factory.imgFactory( new BitType() );
			final Img< BitType > target = bitFactory.create( source, new BitType() );

			final Converter< T, BitType > converter;
			if ( above )
			{
				converter = new Converter< T, BitType >()
				{
					@Override
					public void convert( final T input, final BitType output )
					{
						output.set( input.compareTo( threshold ) > 0 );
					}
				};
			}
			else
			{
				converter = new Converter< T, BitType >()
				{
					@Override
					public void convert( final T input, final BitType output )
					{
						output.set( input.compareTo( threshold ) < 0 );
					}
				};
			}

			final Vector< Chunk > chunks = SimpleMultiThreading.divideIntoChunks( target.size(), numThreads );
			final Thread[] threads = SimpleMultiThreading.newThreads( numThreads );

			if ( target.iterationOrder().equals( source.iterationOrder() ) )
			{
				for ( int i = 0; i < threads.length; i++ )
				{
					final Chunk chunk = chunks.get( i );
					threads[ i ] = new Thread( "Thresholder thread " + i )
					{
						@Override
						public void run()
						{
							final Cursor< BitType > cursorTarget = target.cursor();
							cursorTarget.jumpFwd( chunk.getStartPosition() );
							final Cursor< T > cursorSource = source.cursor();
							cursorSource.jumpFwd( chunk.getStartPosition() );
							for ( long steps = 0; steps < chunk.getLoopSize(); steps++ )
							{
								cursorTarget.fwd();
								cursorSource.fwd();
								converter.convert( cursorSource.get(), cursorTarget.get() );
							}
						}
					};
				}
			}
			else
			{
				for ( int i = 0; i < threads.length; i++ )
				{
					final Chunk chunk = chunks.get( i );
					threads[ i ] = new Thread( "Thresholder thread " + i )
					{
						@Override
						public void run()
						{
							final Cursor< BitType > cursorTarget = target.cursor();
							cursorTarget.jumpFwd( chunk.getStartPosition() );
							final RandomAccess< T > ra = source.randomAccess( target );
							for ( long steps = 0; steps < chunk.getLoopSize(); steps++ )
							{
								cursorTarget.fwd();
								ra.setPosition( cursorTarget );
								converter.convert( ra.get(), cursorTarget.get() );
							}
						}
					};
				}
			}

			SimpleMultiThreading.startAndJoin( threads );
			return target;
		}
		catch ( final IncompatibleTypeException e )
		{
			e.printStackTrace();
			return null;
		}
	}

}
