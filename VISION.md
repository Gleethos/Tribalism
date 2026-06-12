
# Tribalism - Vision

Tribalism is supposed to be the ultimate helper tool for Pen & Paper
game masters. A game master can create dungeon maps in a 3D voxel engine,
campaigns and character sheets for players.

Although it is primarily a desktop application, it also serves as a web server serving
character sheets to clients in the local LAN. Players can see and interact with their characters
through the web interface hosted by the GMs Tribalism client...

On top of that however, tribalism is also a sand boxed agentic AI harness similar to clause code / open code
/ open claw / ... But it is specifically designed to be used as an agent which acts as a DnD game master OR 
a helper for a human GM.

Tribalism is a desktop application with support for local models and configuring custom AI providers.
Therefore it is important to have good security.

The game master / game master copilot should not be allowed to run rampant on the file system
of a user. At the same time, the AI agent should have their own file system and software environment as a playground because that is what
modern AIs are trained to operate in. The agent can then use this custom linux environment to do... whatever!
This means tool calling like mkdir, pwd, ls, cd, curl, bash scripts maybe even python scripting.

Furthermore, mutations to the workspace of the agent is entirely tracked through git from outside.
This is important to do advanced state management through time, checking out previous points
in time and continuing from there. So both the context of an agent and the working space is snapshot
from time to time when the user wants to safe progress...

### Maps

Tribalism ships with a custom voxel engine which is used as a basis for
building maps. There are three modes for how to interface with a map:
1. Creating a map: God mode -> fly around + advanced tools for map editing and PIO generation.
2. Walk around in first person, destroy blocks, basically survival mode in Minecraft
3. Pen & Paper play session mode: Top down with a view layer over the heads of the players.
   So the cameras view is not obstructed when players are in a building...

Worlds can also be procedurally generated. But in the short run there is just a simple set of
generators for simple things like objects (chars, tables, other furniture, houses, etc....)

## Environment

For sandboxing it is probably best to ship a podman binary and then a git binary.
Podman gets full control over a hidden directory in the apps installation dir and then
the application wraps it in a git repo. The harness loop is in the application and tool
calls are directly passed to podman.

## Frontend

Desktop (main interface): SwingTree
Webportal: React
State management: Sprouts (immutable collections and reactive properties)

## Modeling

This application is based on functional and data oriented principles.
So immutable objects are preferred and modifications ought to happen using
the lens pattern. This is also part of the vision for persistance.
Currently, we store objects on the database as `dal.api.Model`, instead
these should only be used as time stamped roots which wrap larger `dal.api.Value`
based data structures (typically records with `Tuple` and `Association`s in them).

So the current model interfaces are an early prototype which needs to change alot...

## Applications frontend

Users can log into the application both from the web portal AND
the desktop application side. Every GUI exists both as react frontend and
SwingTree frontend. The frontend layer is extremely thin because the view models
all live on the Java side and the properties are automatically binding to both frontend.
The react frontend uses a websocket based translation layer...
(see current implementation)

The map is rendered on the application side both for the game master
and the web frontend users. Interactions are translated through a web sockets.

## Application structure

This is where we lack most of the specification.
What should the GUI layout be long term? What modeling do we need?
If we break it down, what even are the features we need?