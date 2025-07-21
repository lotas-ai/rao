# Metagenomics Analysis Demo

[Rao](https://www.lotas.ai/) is a fork of RStudio with a fully integrated AI assistant that can read, write, and edit code; search for context; run code and commands; and view and interpret outputs. Its source code is [available here](https://github.com/lotas-ai/rao). If you have questions or bug reports, please direct them to the [Lotas Forum](https://community.lotas.ai/). 

## 1. Installing R and Rao

#### Installing R for the first time

You can download and install R [here](https://cloud.r-project.org/). You should download the latest release (and no older than version 4.0) - this will ensure the R version you have is compatible with Rao.

#### Installing Rao

1. Go to the [Rao download page](https://www.lotas.ai/download) and download Rao for your machine.
2. Go to the [Account page](https://www.lotas.ai/account), make an account if necessary, and copy your API key.
3. Open the app, click on the AI pane in the top right (or enable it if necessary, see ["Opening the AI Pane"](https://www.lotas.ai/download)), click on the Settings gear in the top left of the pane, and paste in your API key.

Once you've done this, you should see the following section (you might need to click the gear to refresh):
<img src="https://github.com/lotas-ai/rao/blob/ec101d9c83242433c22ca2d005b415b02c77a4e8/demos/media/images/rao-profile.png" style="width:75%;">

For the tutorial, you should also set the "Temperature" to 0 so that the outputs are deterministic. (Outputs will still vary between people for technical reasons related to how Rao provides context, but the variability should be low.) Clearing your environmental variables with `remove(list = ls())` is also recommended to increase consistency.

#### Setting a working directory

Rao works by indexing your current directory so that it can find files, functions, code blocks, etc. more quickly. Therefore, it's helpful to set your working directory to a narrow directory where you'll perform your analysis. You can check your current working directory with `getwd()` and then create a folder for this demo like:

```
dir.create('metagenomics_tutorial')
```

Then, use the "Browse..." button of the "Working Directory" section or use `setwd()` to set your working directory to the folder you create.

## 2. Downloading demo data

The demo data consists of metagenomic data and metadata from the [HMP2 Inflammatory Bowel Disease Multi-omics (IBDMDB) dataset](https://www.ibdmdb.org/). It can be downloaded with the following commands:
```
dir.create("data", showWarnings = FALSE)
download.file("https://raw.githubusercontent.com/lotas-ai/rao/refs/heads/main/demos/examples/metagenomics/data/hmp2_metadata_2018-08-20.csv", 
              "data/hmp2_metadata_2018-08-20.csv", 
              method = "curl")
download.file("https://raw.githubusercontent.com/lotas-ai/rao/refs/heads/main/demos/examples/metagenomics/data/metaphlan4_taxonomic_profiles.tsv", 
              "data/metaphlan4_taxonomic_profiles.tsv", 
              method = "curl")
```

You can inspect the files in your usual tabular data viewer with:
```
browseURL("data/hmp2_metadata_2018-08-20.csv")
browseURL("data/metaphlan4_taxonomic_profiles.tsv")
```
Notice that the metadata contains hundreds of columns with lots of missingness, the taxonomic data has a single line comment header, samples are named differently, etc.

## 3. Making a request

Now, we can make a request for a common metagenomics first-step. Open a new conversation with the `+` button in the top left and paste in this query. Hit enter to run.
```
I have a set of taxonomic abundances with metadata. Plot me a PCoA at the species level with coloring by diagnosis status in an R file. The taxonomic profiles have _taxonomic as their sample suffix.
```
The model will take a series of actions including listing files, reading files, running console commands, and eventually editing an R file to write the PCoA script. Note that it must perform many steps like properly reading the taxonomic abundance table, identifying how diagnosis is encoded, determining how to match the samples, and troubleshooting 0 abundance species.

The script likely won't be perfect; for example, it might use both `s__ABC` and `s__ABC|t__XYZ` taxa in the PCoA rather than just the species level `s__ABC`. You could ask it to fix this with a follow-up request:
```
You included both s__ and t__ taxa, but only the s__ taxa are species. Fix this.
```
Alternatively, if the model only matches 207 samples, you might need to remind it to remove the `_taxonomic` suffix:
```
You did not match the taxonomic profiles correctly. Every taxonomic profile should have exactly one metadata entry. You should remove the _taxonomic suffix to get the proper ID.
```

With these corrections, it should produce a plot like this: 
<img src="https://github.com/lotas-ai/hutlab-demo/blob/d50b49a6986ee4a8a518cdce950887e85a802f98/media/images/pcoa.png" style="width:75%;">

## 4. Further exploration

Some other functionality likely not activated by the query above includes:
- Attaching files with `@Add context` (click the button, drag files from the editor to it, or copy-paste lines from the editor into the search). This indicates to the model that it should probably look at these files.
- Attaching images (bottom left button or drag-drop from the plots pane). The model will automatically view them.
- Searching the web
- Searching your code base
- Running terminal commands
- Tracking changes since the beginning of the conversation in the left gutter. New lines are in green and deleted lines are visible with a red arrow drop-down.

If you have feature requests, let us know on the [Lotas Forum](https://community.lotas.ai/)! 