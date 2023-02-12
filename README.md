# LM SD WebUI Client

This is just a quick and dirty GUI client app for the [AUTOMATIC1111 Stable Diffusion Web UI](https://github.com/AUTOMATIC1111/stable-diffusion-webui). My workflow for generating images is very different than what the Web UI system is built for, so instead of fumbling around in the Python code of the UI, I just decided to use the REST API that comes with the Web UI project and control everything workflow-oriented myself.

### How to run
1. Clone [the AUTOMATIC1111 webui project](https://github.com/AUTOMATIC1111/stable-diffusion-webui)
2. Start it with at least `--api`, but preferably with `--xformers --api` for improved performance
3. Clone this repo
4. Run `de.lostmekka.sdwuic.MainKt.main`

### The workflow
I am used to generate huge amounts of images for a prompt and manually delete all images I dont like. This way I can cherry-pick the best 1% of all the images and do some post processing, in-/outpainting and upscaling later on.

When the generator is started via this client GUI, it continuously instructs the webui API to generate images with the current settings. The resulting image files are stored in the staging directory, where I can manually sift through them. The file names are structured like `<configID>_<batchID>_<fileID>.png`.
- `configID` is the ID of the current run. It is read from the config ID file (see below) and is incremented every time a new config is created. This happens when you start the generator, or when you change some generator settings and then apply those to the currently running generator.
- `batchID` counts the number of batches that were generated in the current config. A batch typically contains 4 to 8 images that are generated in parallel on the GPU.
- `fileID` denotes the position of the file in the batch.

For every started config, the app also saves a text file containing all config parameters that were used for this run, so you can run this specific prompt again later.

### Configuration
Configuration is read from a file named `config.properties` located in the root directory of this repo. It will be created on first startup, but you can create it yourself first, if you want.
| Config key         | Default value           | Description                                                                          |
|--------------------|-------------------------|--------------------------------------------------------------------------------------|
| `apiBasePath`      | `http://127.0.0.1:7860` | Base API path of the AUTOMATIC1111 webui                                             |
| `configIdFilePath` | `out/nextConfigId.txt`  | Path to a text file that will store the next config ID                               |
| `stagingDirPath`   | `out/staging`           | Path to the directory that will receive all generated images and config descriptions |
