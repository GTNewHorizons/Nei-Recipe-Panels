# NEI Recipe Panels

An [NEI](https://github.com/GTNewHorizons/NotEnoughItems) add-on for Minecraft 1.7.10
and GT New Horizons. Display recipes as panels, including items, fluids,
and your selected ingredient alternatives.

1. Craft a **Recipe Blueprint**.
2. Open a recipe in NEI, choose your preferred alternatives, and click the panel button
   next to the favourite / overlay buttons to imprint it, consuming the blueprint. The
   button only shows while you carry a blueprint (or are in creative). NEI Custom Diagram
   pages, such as GregTech ore processing, are supported too.
3. Place the resulting **Recipe Panel** against a solid block face.

- **Right-click** a panel to reopen its recipe in NEI.
- **Sneak-right-click** to rename it or hide its background.
- Use NEI's recipe and usage keys over panel items.
- Craft a panel back into a blank blueprint to reuse it.
- Items animate within 3 blocks; recipe backgrounds and distant panels stay static.

After pack updates, panels show the current recipe and keep your selected items when
possible. If a recipe is missing or cannot be matched uniquely, the panel shows a notice;
re-imprint it to choose a replacement.

## Client config

- `textureBudgetMB` (default 192): video memory for cached panel images; panels beyond
  it show a capacity notice.
- `supersample` (default 3): panel image resolution; lower fits more panels.
- `buttonRequiresBlueprint` (default true): set false to always show the NEI button.

## License

MIT — see [LICENSE](LICENSE).
