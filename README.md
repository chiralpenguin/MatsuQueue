# MatsuQueue (待つQueue)
This is a fork of [MatsuQueueBungee](https://github.com/EmotionalLove/MatsuQueueBungee) by [EmotionalLove](https://github.com/EmotionalLove). The plugin has been updated for Bungeecord 1.15 and specifically adapted for use by [Purity Vanilla](https://www.purity-vanilla.xyz/).

## About
Running a single-world survival server with a large player count can be resource intensive. Instead of just reducing the max player limit and letting your players play the "reconnect lottery", why not create a queue to wait in? This plugin allows that and is simple to set up. If the server's full, they'll be  sent to the queue server automatically and will be sent to the destination server once there is a space available.

This plugin was originally designed for networks with only 2 servers, the destination server and the queue server. This fork adds support for running other servers on your network which you want unaffected by the queue. Players who join the server will be able to use "/server <servername>" or any other custom setup to change to other servers on your network, without affecting the queue. Additionally, more new features have been added to the plugin to help the server owner and to make the experience more streamlined for the players.

## Features
This is a non-exhaustive list of this plugin's features. For more detail and setup help, see the [startup guide](https://github.com/nitricspace/MatsuQueue/wiki/Getting-Started).

#### Multiple Slot Types
The destination server can have multiple "slot types" to assign players to based on their permissions. For example, server may have 80 default slots and 20 reserved slots. With this setup, 80 regular players can join your server before the queue will start for regular players. Players joining with the reserved slot permission will only take up those reserved slots, allowing donors to have their own seperate playerlimit and their own queue.

#### Customisable Queues and Priorities
Each slot type can have multiple queues associated with them. Using the above example, the default slot type may have two queues; standard and priority. If a player with the priority queue permission joins the queue for the default slot type, they will skip ahead of all players in the standard queue. All of this is completely customisable within the configuration file, including the priorities, queue names and permission nodes.

#### Support for Multiple Servers
The Bungeecord network may have other servers running on the network that are unaffected by the queue. When a player moves to one of these servers they are removed from the queue and/or removed from their slot so that another player in the queue can take their place.

#### Change Server Capacity While Running
The capacity of a slot type can be changed while running, to allow more players to join during peak time or to reduce the playercount if the server is lagging. Simply change the capacity in the configuration file and run "/updateslots" in the console. If the capacity has increased, players will be sent from the queue until the queue is empty or the new capacity is reached. If the capacity has decreased, players will not be sent from the queue server until enough players have left the destination server to meet the new capacity.

## Setup
- Ensure you are running the latest version of Bungeecord, available [here](https://ci.md-5.net/job/BungeeCord/).

- Download the latest JAR file from our [releases](https://github.com/nitricspace/MatsuQueue/releases) and move it to your Bungeecord plugins folder.

- Run the Bungeecord instance to generate the config file, then stop it.

- Edit the config file to your needs, ensuring that the names of the servers match with that in your Bungeecord configuration. Use [this](https://github.com/nitricspace/MatsuQueue/wiki/Configuration-File) page for help.

- If needed, install the [MatsuQueueBukkit Companion Plugin](https://github.com/EmotionalLove/MatsuQueueBukkit) on the backend servers for a configurable queue environment.

- Run your Bukkit and Bungeecord servers and ensure your config is working as intended. (You can test by setting the MatsuQueue player limit to 1 or 2 and opening multiple instances of Minecraft.)

## Issues, Troubleshooting and New Features
A list of current issues with the plugin and new features can be found [here](https://github.com/nitricspace/MatsuQueue/issues). Please check that your issue is not already listed there before opening a new issue, or requesting a new feature.

If you are opening a new issue to report a problem, please include a logfile from your Bungeecord server with "verbose: true" in the config file and give the timestamp of when the issue occurs (if possible).
