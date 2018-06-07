/* Soot - a J*va Optimization Framework
 * Copyright (C) 2004 Ondrej Lhotak
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

package de.upb.soot.ns.classprovider;

import java.nio.file.Path;
import java.util.Optional;

import de.upb.soot.ns.INamespace;

/** Responsible for handling various types of class sources (.class, .jimple, .java, .dex, etc) */
public interface IClassProvider {

  // TODO does the class provider need the signature or does it generate one
  Optional<ClassSource> getClass(INamespace ns, Path sourcePath);

  /**
   * Used to filter for files which are handled by the concrete ClassProvider, e.g. *.class,
   * *.jimple, etc
   *
   * @param sourceFile
   * @return
   */
  boolean handlesFile(Path sourceFile);
}
