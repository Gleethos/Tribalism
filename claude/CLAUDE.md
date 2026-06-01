
# Principles

First up. This project is heavily centered around functional programming and data oriented programming.
This means that most data structures, no matter how large or deep, are immutable and have value semantics.
In practice, this means using records, sum types (sealed interfaces implemented by records) and persistent
data structures running through functions. Performance is achieved through structural sharing, lazy values, 
and heavy memoization.

The persistent collections are provided by Sprouts! Using `Tuple` for immutable arrays,
`Association` for immutable maps and `ValueSet` for immutable sets. You can find the library in the dependencies...

# Long Term

Okay so this application is going to need a rendering engine in the future.
And like any engine it must be based on a system which manages the 3D world.
Let's call it a world engine. The rendering is not so important. It is merely 
going to be a pluggable function of the state of the world...

# The World Engine

The world engine is entirely located in 64bi simulation space. So all doubles.
Conversion to 32bit floats happens through dynamic scaling in the rendering step,
which is not yet important...
But essentially, all primitive data types, like VecF64, BoundsF64, CameraF64, ... are in 64bit...
I suggest focusing on creating math primitives first.

This core of the world engine consists of a tree data structure heavily inspired by Hash Array Mapped Tries. 
A similar thing as an Oc-Tree, but much more optimized for modern hardware.

Each node called `WorldTreeNode` consists of CPU cache friendly `Tuple` of exactly `256` instances of `WorldSection` records.
These 256 sections form a perfect 3D cube consisting of these little world sections which are themselves cubes.
So a "world section" has a bounding box (`BoundsF64`).


# World Sections

## Meta Info

Sections carry certain material properties by referencing a record called `WorldSectionEtherData`
which consists of a set of numbers which are percentages of what this world section is made of.
For example 90% air, 5% soil, 5% rock etc...
This information is later on used to render a section in a certain color and shape,
as well as reflective properties and other things.
It is also used as a basis for level of detail computation.

## Entities

Then a `WorldSection` also references a `ValueSet` of `WorldTreeEntityId`s.
These entities are not the actual entities in the world engine. The actual entities,
live in a separate fast lookup based data structure. Instead, these "tree entity ids"
are simply the unique id (long) of an entity, and its bounding box.
If we were to stored more information about an entity on the world tree, then for updates
to the entity, we would also need to traverse and update the tree, which we want to avoid.
But by only using the tree a positional entity lookup, we only need to update an entity in the tree, 
if its bounding box (position and size) changes... 

(which will not happen as often for this type of application)

## Lights

A light is very similar to an entity. It also has a unique id and a bounding box.
They are stored in a `ValueSet` of a type called `LightSource`. 
It is a sum type, where each implementation describes a light as a simple shape
(sphere, plane, cube, etc) and a position as well as some basic light properties like intensity
and color, etc...

## Light Trace

Besides actual lights with their own identity, there are also "light traces".
The final design goal is simple: A light trace consists of a vector, an intensity
and the id of the actual light source. When the world engine is actually running,
the update cycle of the engine will place these traces in the tree so that they slowly
radiate away from the light source into multiple sections (with a certain threshold
depending on the intensity).

## Branching

Ok and finally, a world section, is a recursive thing, it can itself reference either `null` or a `WorldTreeNode`.
And so this is where the data structure gets interesting. It essentially has infinite resolution.
A world section can be split into another 256 subsections and if entities are small enough, they can fall
down and be distributed among them... 

## The Big Picture

This data structure is specifically designed to have almost infinite
scale into the large and infinite resolution into the small. A "world section"
can serve both as a simple voxel (`WorldTreeNode` is null and ether data says 100% rock), or
it can serve as a high level section in the world containing a mix of all kinds of things.
The great thing about this is that we can easily generate LoDs from these sections.
So a section computes its "ether data" from its sub-sections whenever they change,
and this ether data can be used to compute a voxel which essentially averages the world section
visually as well...

Now, of course. This will end up looking kind of Mine-crafty-
But that is fine. The important thing is the potential.

--- 

Now. Your goal is to build this thing based on the spec above.
Rendering comes in a second step, as well as the update loop and entity state management. 
For now all we need is a data structure, math primitives and well documented and thought through code.
If you have questions. Please ask.










