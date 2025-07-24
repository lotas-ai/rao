# Italian COVID-19 Opinions Demo

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
dir.create('italy_covid')
```

Then, use the "Browse..." button of the "Working Directory" section or use `setwd()` to set your working directory to the folder you create.

## 2. Downloading demo data

The demo data is from ["Evaluating COVID-19 Public Health Messaging in Italy: Self-Reported Compliance and Growing Mental Health Concerns"](https://dataverse.harvard.edu/dataset.xhtml?persistentId=doi:10.7910/DVN/1SBQCX). It can be downloaded with the following commands:
```
dir.create("data", showWarnings = FALSE)
download.file("https://raw.githubusercontent.com/lotas-ai/rao/refs/heads/main/demos/examples/italy_covid/data/00-read_clean_data.R", 
              "data/00-read_clean_data.R", 
              method = "curl")
download.file("https://raw.githubusercontent.com/lotas-ai/rao/refs/heads/main/demos/examples/italy_covid/data/05-analyze_stayhome.R", 
              "data/05-analyze_stayhome.R", 
              method = "curl")
download.file("https://raw.githubusercontent.com/lotas-ai/rao/refs/heads/main/demos/examples/italy_covid/data/mar18_responses.csv", 
              "data/mar18_responses.csv", 
              method = "curl")
setwd('data')
dir.create('fig')
```

You can inspect the file in your usual tabular data viewer with:
```
browseURL("data/mar18_responses.csv")
browseURL("data/05-analyze_stayhome.R")
```
Notice that the `model demographic predictors` section of the R file is listed as TODO.

## 3. Making a request

Let's ask Rao to fill in this section. Open a new conversation with the `+` button in the top left and paste in this query. Hit enter to run.
```
Notice the "TODO" at the end of the file - look at the data and write this section. It should be concise, only 2 plots.
```

The model will edit the file to run some demographic analyses and likely save the plots to the same `fig` folder.

<img src="https://github.com/lotas-ai/rao/blob/16d733b607f62e12bae08afbd9faab256940c001/demos/media/images/italy_covid_opinion.png" style="width:75%;">

## 4. Further exploration

Some other functionality likely not activated by the query above includes:
- Attaching files with `@Add context` (click the button, drag files from the editor to it, or copy-paste lines from the editor into the search). This indicates to the model that it should look at these files.
- Attaching images (bottom left button or drag-drop from the plots pane). The model will automatically view them.
- Searching the web
- Searching your code base
- Running terminal commands
- Tracking changes since the beginning of the conversation in the left gutter. New lines are in green and deleted lines are visible with a red arrow drop-down.

If you have feature requests, let us know on the [Lotas Forum](https://community.lotas.ai/)! 
