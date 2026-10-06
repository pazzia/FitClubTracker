# Exercise thumbnail contrast

64 reviewed second-frame exercise photographs have dedicated thumbnail assets in
`app/src/main/assets/exercise_thumbnails`. Dark upper-body clothing is orange to
separate the athlete from the gym. These raster edits retain the exercise pose
and active equipment, with a gently brighter and softer background.

`ExerciseThumbnail` uses these assets only for list thumbnails. Exercise detail
photos continue to load the original `free_exercise_db` images. The original
448-photo SHA-256 manifest remains unchanged.

Other thumbnails use the athlete-and-equipment matte to preserve the foreground
while gently brightening and blurring the background. Images use `ContentScale.Fit`
to keep the complete equipment visible.

`exercise_thumbnails/manifest.json` records the original and prepared image hashes.
`PhotoPresentationTest` checks all 64 bundled images against this manifest and
renders the entire source catalog through Android's native bitmap implementation
for visual review. The CI artifacts include these contact sheets and larger
exercise examples.
