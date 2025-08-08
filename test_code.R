
# Load the data and examine its structure
library(readr)
library(dplyr)

# Read the data
data <- read_csv("data/electricity_data.csv")

# Check dimensions
cat("Dataset dimensions:", dim(data), "
")

# Look at unique series to understand what metrics we have
unique_series <- unique(data$`Series Name`)
cat("
Available series:
")
print(unique_series)

# Check for African countries - look for some common African country names
african_countries <- data %>% 
  filter(grepl("Africa|Nigeria|Kenya|Ghana|Ethiopia|South Africa|Morocco|Egypt|Tanzania|Uganda|Rwanda|Senegal|Mali|Burkina|Chad|Niger|Cameroon|Madagascar|Mozambique|Angola|Zambia|Zimbabwe|Botswana|Namibia|Malawi|Benin|Togo|Guinea|Sierra Leone|Liberia|Ivory Coast|Gabon|Congo|Sudan|Algeria|Tunisia|Libya", `Country Name`, ignore.case = TRUE)) %>%
  distinct(`Country Name`) %>%
  arrange(`Country Name`)

cat("
African countries found (", nrow(african_countries), "):
")
print(african_countries$`Country Name`)

