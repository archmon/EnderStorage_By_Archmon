# VoidStorage by Archmon for Hytale
Originally the template for Hytale java plugins modified by Archmon
from what Kaupenjoe on YouTube released as part of his Hytale Modding Tutorial.

Disclosure:
This is my first mod and very much a work in progress. This mod is meant to bring the
EnderStorage mod from Minecraft to Hytale. I used the videos from Kaupenjoe and Ali
 to get the start of the mod, and the Ai from Google.com search was used in
this mod's creation. I also did heavily use the AI from intellij jetbrains/codex to help
me with the development of this mod.

## Current Features

- Pocket Dimension Safe: A private database-backed storage block keyed by the placement location. This means you can have 
them next to each other, and they each have their own inventories that only you can access (not even automation can). 
And only you can remove it.
  (Or at least you or an admin can.) And when the safe is destroyed, all the contents are dropped, so nothing is lost.
- Void Chest: A shared network chest using color-code channels with 4096 possibilities. (Use the Void Wrench to change the
channel code). 
And by adding Adamantite Ingot to the chest (also by use of the Void Wrench), 
you can get access to the private network chests for even more.
Each player can have their own network in addition to the shared one. 
- VoidWrench: A wrench that while held can be used to open the Void Chest gui while crouching. (By default, it's Control-F).



## API Access for other mods to see the Void Chest

To access the Void Chest, other mods can use the VoidStorage API. 
The API provides methods to interact with the Void Chest, such as depositing and withdrawing items. 
This allows other mods to utilize the Void Chest for storage purposes. 
Please refer to the methods within the VoidStorageAPI class to use the API. 
Please note that the Pocket Dimension Safe and Void Wrench are not part of the API. Only the Void Chest is, and even then,
it is just to allow other mods to see and interact with the inventory of the Void Chest. This was necessary because of the
issues with the world-sided chest. (See Known Issues)

## Known Issues
- The Void Chest is meant to have the buttons on the top, but due to the way the blockstates work, that means I need 8092 model
  files for just showing all the different animation files of the Void Chest. Currently, I have 2 blockstates for the Void chest.
- When click dragging to deposit into chests, items bounce back to the players' inventory. (This is a hytale bug.)
- Trying to swing the void wrench while crouching doesn't work because I wasn't able to get the necessary event to fire.
- Access to the Void Chest has to be implemented by other mods by way of my API so that they can use the Void Chest.
  Without this, there is no way for other mods to access the Void Chest.
  This is because I tried importing from the world side of the chest and found issues with that way of doing things.
  For instance, I could use the world side as an import buffer, but then if the chest was full, items would still be
  in the inaccessible world-sided chest until such time as the Void Chest cleared out or the instance of that void chest
  was destroyed. I also tried making a bridge between the world-side and the database-side chests, but that also had issues
  like duplication of items. I would have loved to make it all work by making it all one inventory like the Minecraft mod
  does, but the hook for that was not available.