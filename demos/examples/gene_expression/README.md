# Gene Expression Analysis Demo

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
dir.create('gene_expression_tutorial')
```

Then, use the "Browse..." button of the "Working Directory" section or use `setwd()` to set your working directory to the folder you create.

## 2. Downloading demo data

First, install `curatedTCGAData` as follows:

```
if (!require("BiocManager", quietly = TRUE))
    install.packages("BiocManager")

BiocManager::install("curatedTCGAData")
```

Copy the following lines into a new file called `BRCA.R` in your directory.

```
library(curatedTCGAData)
set.seed(1)

tcga_br <- curatedTCGAData("BRCA", assays = "RNASeq2GeneNorm", dry.run = FALSE, version = '2.0.1')
meta <- colData(tcga_br)[,1:100]
expr <- assay(tcga_br[[1]])[sample(1:nrow(assay(tcga_br[[1]])), 1000),]
```

## 3. Making a request

Now, we can make a request for gene expression analysis. Open a new conversation with the `+` button in the top left and paste in this query. Hit enter to run.
```
Look at my gene expression data and its metadata and plot a visualization of the samples. Once you've done this, write a very brief Rmd with the points colored by tumor stage.
```

The model will take a series of actions including listing files, reading files, running console commands, and eventually creating visualizations and an R Markdown document. Note that it must perform many steps like properly reading the gene expression data, identifying how tumor stage is encoded, determining how to match the samples, and creating appropriate visualizations.

## 4. Further exploration

Some other functionality likely not activated by the query above includes:
- Attaching files with `@Add context` (click the button, drag files from the editor to it, or copy-paste lines from the editor into the search). This indicates to the model that it should probably look at these files.
- Attaching images (bottom left button or drag-drop from the plots pane). The model will automatically view them.
- Searching the web
- Searching your code base
- Running terminal commands
- Tracking changes since the beginning of the conversation in the left gutter. New lines are in green and deleted lines are visible with a red arrow drop-down.

If you have feature requests, let us know on the [Lotas Forum](https://community.lotas.ai/)! 