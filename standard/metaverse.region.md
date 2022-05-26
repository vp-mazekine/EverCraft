## JSON metadata type: `metaverse.region`

The `metaverse.region` JSON object stores the unique reference to a specific region (bundle of regions) inside a metaverse.

| Field name | Type | Description | Value |
| --- | --- | --- | --: |
| **type** | string | NFT type | `metaverse.region` |
| **meta** | [object](#region-metadata) | Region metadata | |
| **regions** | [array&lt;object&gt;](#region-shape-types) | Region parameters (multi-component regions allowed) | |

**Example**
```json
{
	"type": "metaverse.region",
	"meta": {
		"server": {
			"type": "minecraft",
			"url": "http://10.10.10.10:25565",
			"pubkey": "c843d424bba89625d64fb592975180023e46b499388856fa832e287484adc4aa"
		},
		"signature": "cc9d3377f78d33a3d2d412d173f2b6e9e6dd06af19749d95032ea7c78eb07d873ddfe0b241a190900422732207dbbdc987b1bbcfd74d56404f0ab0d65d4f930e"
	},
	"regions": [
		{
			"shape": "cuboid",
			"params": {
				"position1": {
					"x": "0",
					"y": "-50",
					"z": "0"
				},
				"position2": {
					"x": "16",
					"y": "200",
					"z": "16"
				}
			}
		}
	]
}
```

### Region metadata

This section must contain at least a minimum of the required information about the metaverse server where the region is located.

| Field name | Type | Description | Example value |
| --- | --- | --- | --: |
| **server.type** | string | Type of metaverse that has issued the NFT token | `minecraft` |
| **server.url** | uri (optional) | Address where the server is located. Typically, the URL, from which the server actual metadata like name, description, etc. can be extracted. | `10.10.10.10:25565` |
| **server.pubkey** | string | Public key issued by the compatible NFT adapter used to sign the NFT content data | |
| **signature** | string | HEX representation of the signature, generated using ed25519 algorithm based on the contents of `regions` array content (without the object name) and server private key.<br/><br/>Before generation, the base JSON array must be compacted (i.e. all the whitespace and line breaks must be removed). In the example above, it will look as follows:<br/><br/>`[{"shape":"cuboid","params":{"position1":{"x":"0","y":"-50","z":"0"},"position2":{"x":"16","y":"200","z":"16"}}}]` | |

### Region shape types

The reference implementation is inspired by Minecraft's Worldedit primitive region models.

| Type | Description | Reference model |
| --- | --- | --- |
| **cuboid** | Box-shaped region | [Link](https://github.com/EngineHub/WorldEdit/blob/b8a9c0070c72bbdd0d2c77fa8c537c01b0f73f85/worldedit-core/src/main/java/com/sk89q/worldedit/regions/selector/CuboidRegionSelector.java) |
| **cylinder** | Cylinder-shaped region | [Link](https://github.com/EngineHub/WorldEdit/blob/b8a9c0070c72bbdd0d2c77fa8c537c01b0f73f85/worldedit-core/src/main/java/com/sk89q/worldedit/regions/selector/CylinderRegionSelector.java) |
| **sphere** | Spheric-shaped region | [Link](https://github.com/EngineHub/WorldEdit/blob/b8a9c0070c72bbdd0d2c77fa8c537c01b0f73f85/worldedit-core/src/main/java/com/sk89q/worldedit/regions/selector/SphereRegionSelector.java) |
| **polygon2d** | 2D-polygonal-shaped region | [Link](https://github.com/EngineHub/WorldEdit/blob/b8a9c0070c72bbdd0d2c77fa8c537c01b0f73f85/worldedit-core/src/main/java/com/sk89q/worldedit/regions/Polygonal2DRegion.java#L39) |

#### Cuboid

**Cuboid** is the simplest and the most popular shape of a region typically used in most metaverses.

It is defined by X, Y and Z coordinates of two points in the space (the order is insignificant), one of which is considered as the start (bottom, minimum) and the other one as the end (top, maximum) of the region.

##### Cuboid object specification

| Field name | Type | Description | Value |
| --- | --- | --- | --: |
| **shape** | string | Type of shape | `cuboid` |
| **params.position1** | [xyz](#xyz) | First vertex of the cuboid | |
| **params.position2** | [xyz](#xyz) | Second vertex of the cuboid | |

##### Example usage

```json
{
	"type": "metaverse.region",
	"meta": {},
	"regions": [
		{
			"shape": "cuboid",
			"params": {
				"position1": {
					"x": "0",
					"y": "-50",
					"z": "0"
				},
				"position2": {
					"x": "16",
					"y": "200",
					"z": "16"
				}
			}
		}
	]
}
```

#### Cylinder

**Cylinders** are commonly used in central areas of large objects (lize plazas, fountains, etc).

They are defined by coordinates of basic central point, radius, and height.

##### Cylinder object specification

| Field name | Type | Description | Value |
| --- | --- | --- | --: |
| **shape** | string | Type of shape | `cylinder` |
| **params.center** | [xyz](#xyz) | Basic central point of the cylinder | |
| **params.radius** | string | Radius of the cylinder (number in string format for maximum compatibility) | |
| **params.height** | string | Height of the cylinder (number in string format for maximum compatibility)<br/><br/>For the avoidance of doubt, the Y coordinate of the opposite cylinder's side is calculated as `Y_basic + height` | |

##### Example usage

```json
{
	"type": "metaverse.region",
	"meta": {},
	"regions": [
		{
			"shape": "cylinder",
			"params": {
				"center": {
					"x": "0",
					"y": "-50",
					"z": "0"
				},
				"radius": "16",
				"height": "100"
			}
		}
	]
}
```

#### Sphere

**Spheres** are typically used as parts of complex objects like sculptures, buildings, etc.

Sphere is described by coordinates of central point and radius.

##### Spheric object specification

| Field name | Type | Description | Value |
| --- | --- | --- | --: |
| **shape** | string | Type of shape | `sphere` |
| **params.center** | [xyz](#xyz) | Basic central point of the sphere | |
| **params.radius** | string | Radius of the sphere (number in string format for maximum compatibility) | |

##### Example usage

```json
{
	"type": "metaverse.region",
	"meta": {},
	"regions": [
		{
			"shape": "sphere",
			"params": {
				"center": {
					"x": "0",
					"y": "-50",
					"z": "0"
				},
				"radius": "16"
			}
		}
	]
}
```

#### 2D polygon

**Polygons** can represent an arbitrary set of vertices.

2D polygons assume that all vertices lay on the same plane. The whole object can be extended in height.

##### 2D polygon object specification

| Field name | Type | Description | Value |
| --- | --- | --- | --: |
| **shape** | string | Type of shape | `polygon2d` |
| **params.points** | [list&lt;xyz&gt;](#xyz) | Coordinates of polygon vetices | |
| **params.height** | string | Height of the object (number in string format for maximum compatibility).<br/><br/>For the avoidance of doubt, the Y coordinate of the opposite object's side is calculated as `Y_basic + height`, where `Y_basic` is the `Y` coordinate of the first vertex in the array above. | |

#### Shared models

##### XYZ

| Field name | Type   | Description  | Default value |
| ---        | ---    | ---          | --:           |
| **x**      | string | X coordinate | 0             |
| **y**      | string | Y coordinate | 0             |
| **z**      | string | Z coordinate | 0             |

#### Standard conventions
* Numbers and coordinates interpretation depends on the specific metaverse defined in the `meta` section of the NFT. For this standard purpose, the following convention is applied:
  * `X` axis is a horizontal one pointing East;
  * `Y` axis is a vertical one pointing up;
  * `Z` is the orthogonal axis pointing south;<br/><br/><img src="https://static.wikia.nocookie.net/minecraft_gamepedia/images/5/51/Coordinates.png/revision/latest/scale-to-width-down/200?cb=20200729013357"/>
* Numbers are stored as strings to ensure maximum compatibility;
* Numbers must not use scientific notation.