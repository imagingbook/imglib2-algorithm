package net.imglib2.algorithm.math;

import java.util.Map;

import net.imglib2.algorithm.math.abstractions.ABinaryFunction;
import net.imglib2.algorithm.math.abstractions.OFunction;
import net.imglib2.algorithm.math.execution.Addition;
import net.imglib2.algorithm.math.execution.LetBinding;
import net.imglib2.algorithm.math.execution.Variable;
import net.imglib2.converter.Converter;
import net.imglib2.type.numeric.RealType;

public final class Add extends ABinaryFunction
{
	public Add( final Object o1, final Object o2 )
	{
		super( o1, o2 );
	}
	
	public Add( final Object... obs )
	{
		super( obs );
	}

	@Override
	public < O extends RealType< O > > OFunction< O > reInit(
			final O tmp,
			final Map< String, LetBinding< O > > bindings,
			final Converter< RealType< ? >, O > converter,
			final Map< Variable< O >, OFunction< O > > imgSources )
	{
		final OFunction< O > a = this.a.reInit( tmp, bindings, converter, imgSources ),
	             			 b = this.b.reInit( tmp, bindings, converter, imgSources );

		// Optimization: remove null ops
		if ( a.isZero() )
			return b;
		if ( b.isZero() )
			return a;
				
		return new Addition< O >( tmp.copy(), a, b );
	}
}