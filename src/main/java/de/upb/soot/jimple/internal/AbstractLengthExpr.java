/* Soot - a J*va Optimization Framework
 * Copyright (C) 1999 Patrick Lam
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA 02111-1307, USA.
 */

/*
 * Modified by the Sable Research Group and others 1997-1999.  
 * See the 'credits' file distributed with Soot for the complete list of
 * contributors.  (Soot is distributed at http://www.sable.mcgill.ca/soot)
 */


package de.upb.soot.jimple.internal;

import de.upb.soot.UnitPrinter;
import de.upb.soot.core.ValueBox;
import de.upb.soot.jimple.ExprSwitch;
import de.upb.soot.jimple.Jimple;
import de.upb.soot.jimple.LengthExpr;
import de.upb.soot.jimple.Switch;
import de.upb.soot.jimple.type.IntType;
import de.upb.soot.jimple.type.Type;

@SuppressWarnings("serial")
public abstract class AbstractLengthExpr extends AbstractUnopExpr implements LengthExpr
{
    protected AbstractLengthExpr(ValueBox opBox) { super(opBox); }

    public boolean equivTo(Object o)
    {
        if (o instanceof AbstractLengthExpr)
        {
            return opBox.getValue().equivTo(((AbstractLengthExpr)o).opBox.getValue());
        }
        return false;
    }

    /** Returns a hash code for this object, consistent with structural equality. */
    public int equivHashCode() 
    {
        return opBox.getValue().equivHashCode();
    }

    @Override
    public abstract Object clone();

    @Override
    public String toString()
    {
        return Jimple.LENGTHOF + " " + opBox.getValue().toString();
    }
    
    public void toString(UnitPrinter up) {
        up.literal(Jimple.LENGTHOF);
        up.literal(" ");
        opBox.toString(up);
    }

    public Type getType()
    {
        return IntType.v();
    }

    public void apply(Switch sw)
    {
        ((ExprSwitch) sw).caseLengthExpr(this);
    }
}