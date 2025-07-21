# Electricity Access Analysis Demo

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
dir.create('electricity_tutorial')
```

Then, use the "Browse..." button of the "Working Directory" section or use `setwd()` to set your working directory to the folder you create.

## 2. Downloading demo data

The demo data consists of electricity metrics for countries over time from the World Bank. It can be downloaded with the following commands:
```
dir.create("data", showWarnings = FALSE)
download.file("https://raw.githubusercontent.com/lotas-ai/rao/refs/heads/main/demos/examples/electricity_access/data/electricity_data.csv", 
              "data/electricity_data.csv", 
              method = "curl")
```

You can inspect the file in your usual tabular data viewer with:
```
browseURL("data/electricity_data.csv")
```
Notice that the data includes lots of information related to global electricity access with complicated missingness, non-standard NA values, and specialized data encoding.

## 3. Making a request

Now, we can make a request for analysis. Open a new conversation with the `+` button in the top left and paste in this query. Hit enter to run.
```
Look at my electricity data. Make a concise RMD that shows electricity access in African countries over time.
```

The model will take a series of actions including listing files, reading files, understanding the data structure, and creating an R Markdown document with temporal visualizations. Note that it must perform many steps like properly reading the electricity data, identifying country and time variables, creating appropriate time series visualizations, and generating summary statistics.

The analysis will likely include:
- Overview of electricity access trends across African countries
- Time series plots showing changes over time
- Country/region comparison visualizations
- Interactive or animated visualizations

<img src="https://github.com/lotas-ai/rao/blob/d14933d0aeff0ef9a0e5711ab07e90ab1f0d4433/demos/media/images/africa_energy.png" style="width:75%;">

## 4. Further exploration

Some other functionality likely not activated by the query above includes:
- Attaching files with `@Add context` (click the button, drag files from the editor to it, or copy-paste lines from the editor into the search). This indicates to the model that it should look at these files.
- Attaching images (bottom left button or drag-drop from the plots pane). The model will automatically view them.
- Searching the web
- Searching your code base
- Running terminal commands
- Tracking changes since the beginning of the conversation in the left gutter. New lines are in green and deleted lines are visible with a red arrow drop-down.

If you have feature requests, let us know on the [Lotas Forum](https://community.lotas.ai/)! 