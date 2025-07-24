############################################################
# Read in March 18 responses.
# 
# Author: 
# Soubhik Barari
# 
# Environment:
# - must use R 3.6
# 
# Input:
# - mar18_responses.csv
#
# Output:
# - data_clean.csv
############################################################

library(ggplot2)
library(ggh4x)
library(dplyr)

## read 
responses_df = read.csv("mar18_responses.csv", stringsAsFactors=F)
responses_df = responses_df[4:nrow(responses_df),]

## clean responses, filter
responses_df$Q25 = gsub("exercising, jogging", "exercising/jogging", responses_df$Q25)
responses_df$Q26 = gsub("(\\w\\.|\\t)", "", responses_df$Q26)

responses_df$age_years <- ifelse(responses_df$age == "", NA, 2020 - as.numeric(responses_df$age))
responses_df$age <- ifelse(responses_df$age_years > 100, NA, responses_df$age_years)

responses_df$duration <- as.numeric(responses_df$Duration..in.seconds.)

responses_df <- responses_df %>% filter(treatment %in% c("control", "one", "two"))

## correct factors
responses_df$Q23 <- factor( #public reaction
    responses_df$Q23,
    levels = c(
        "",
        "The reaction is not at all sufficient",
        "The reaction is somewhat insufficient",
        "The reaction is appropriate",
        "The reaction is somewhat too extreme",
        "The reaction is much too extreme"
    )
)

responses_df$perceivedreaction <- factor( #govt rection
    responses_df$perceivedreaction,
    levels = c(
        "",
        "The reaction is not at all sufficient",
        "The reaction is somewhat insufficient",
        "The reaction is appropriate",
        "The reaction is somewhat too extreme",
        "The reaction is much too extreme"
    )
)

responses_df$Q36 <- factor( #trust govt
    responses_df$Q36,
    levels = c(
        "",
        "Strongly distrust",
        "Distrust",
        "Neither trust nor distrust",
        "Somewhat trust",
        "Strongly trust"
    )
)

responses_df$Q37 <- factor( #govt been truthful
    responses_df$Q37,
    levels = c(
        "",
        "Very untruthful",
        "Somewhat untruthful",
        "Neither truthful nor untruthful",
        "Somewhat truthful",
        "Very truthful"
    )
)

responses_df$perceivedeffectivnes <- factor( #effectiveness of social distancing
    responses_df$perceivedeffectivnes,
    levels = c(
        "",
        "Not at all effective",
        "Not effective",
        "Neither effective nor ineffective",
        "Effective",
        "Very effective"
    )
)

responses_df$gender <- factor(responses_df$gender, levels=c("Male", "Female", "Other", ""))
responses_df$treatment <- factor(responses_df$treatment, levels=c("control", "one", "two"))
responses_df$health <- factor(responses_df$health, levels=c("Poor", "Fair", "Good", "Excellent", ""))

## create past behavior index
responses_df$past_behavior_index <- (as.numeric(responses_df$SelfReported_Behavio_1) +
                                         as.numeric(responses_df$SelfReported_Behavio_2) + 
                                         as.numeric(responses_df$SelfReported_Behavio_3) + 
                                         as.numeric(responses_df$SelfReported_Behavio_4) + 
                                         as.numeric(responses_df$SelfReported_Behavio_5))/5

responses_df$past_healthy_behavior <- ifelse(
    responses_df$past_behavior_index < 33, "Low",
    ifelse(
        responses_df$past_behavior_index %in% 34:65, "Medium",
        ifelse(
            responses_df$past_behavior_index > 65, "High", NA
        )))
responses_df$past_healthy_behavior <- factor(responses_df$past_healthy_behavior, levels=c("Low", "Medium", "High"))

## create anxiety index (coding NA's as 0 for now)
responses_df$anxiety_1 <- factor(responses_df$anxiety_1, 
    levels = c(
        "Does not apply at all", "Somewhat does not apply", "Neither applies nor does not apply", "Somewhat applies", "Strongly applies"
    )
)
responses_df$anxiety_2 <- factor(responses_df$anxiety_2, 
    levels = rev(c(
        "Does not apply at all", "Somewhat does not apply", "Neither applies nor does not apply", "Somewhat applies", "Strongly applies"
    ))
)
responses_df$anxiety_3 <- factor(responses_df$anxiety_3, 
    levels = c(
        "Does not apply at all", "Somewhat does not apply", "Neither applies nor does not apply", "Somewhat applies", "Strongly applies"
    )
)
responses_df$anxiety_4 <- factor(responses_df$anxiety_4, 
    levels = c(
        "Does not apply at all", "Somewhat does not apply", "Neither applies nor does not apply", "Somewhat applies", "Strongly applies"
    )
)
responses_df$anxiety_5 <- factor(responses_df$anxiety_5, 
    levels = c(
        "Does not apply at all", "Somewhat does not apply", "Neither applies nor does not apply", "Somewhat applies", "Strongly applies"
    )
)
responses_df$anxiety_index <- ((as.numeric(responses_df$anxiety_1) + 
    as.numeric(responses_df$anxiety_2) +
    as.numeric(responses_df$anxiety_3) +
    as.numeric(responses_df$anxiety_4) +
    as.numeric(responses_df$anxiety_5))/25)*100

## coarsen variables
responses_df$age_group <- ifelse(
    responses_df$age %in% 18:29, "18-29",
    ifelse(
        responses_df$age %in% 30:39, "30-39",
        ifelse(
            responses_df$age %in% 40:49, "40-49",
            ifelse(
                responses_df$age %in% 50:59, "50-59",
                ifelse(
                    responses_df$age >= 60, "60+", NA
                )))))


responses_df$anxiety_group <- ifelse(
    responses_df$anxiety_index < 0.3, "low",
    ifelse(
        responses_df$anxiety_index > 0.3 & responses_df$anxiety_index < 0.6, "med",
        ifelse(
            responses_df$anxiety_index > 0.6,
            "hi", NA
        )
    )
)

responses_df$think_publ_extreme <- ifelse(responses_df$Q23 %in% c("The reaction is much too extreme", "The reaction is somewhat too extreme"), "extreme", 
                                          ifelse(responses_df$Q23 %in% c("The reaction is somewhat insufficient", "The reaction is not at all sufficient"), "insufficient", 
                                                 ifelse(responses_df$Q23 %in% c("The reaction is appropriate"), "appropriate", NA)))
responses_df$think_publ_extreme <- factor(responses_df$think_publ_extreme, levels=c("extreme", "appropriate", "insufficient"))

responses_df$think_govt_extreme <- ifelse(responses_df$perceivedreaction %in% c("The reaction is much too extreme", "The reaction is somewhat too extreme"), " extreme",
                                          ifelse(responses_df$perceivedreaction %in% c("The reaction is somewhat insufficient", "The reaction is not at all sufficient"), " insufficient", 
                                                 ifelse(responses_df$perceivedreaction %in% c("The reaction is appropriate"), " appropriate", NA)))
responses_df$think_govt_extreme <- factor(responses_df$think_govt_extreme, levels=c(" extreme", " appropriate", " insufficient")) ##note spaces

responses_df$think_effec <- ifelse(responses_df$perceivedeffectivnes %in% c("Effective", "Very effective"), " effective", 
                                   ifelse(responses_df$perceivedeffectivnes %in% c("Not effective", "Not at all effective"), " not effective", 
                                          ifelse(responses_df$perceivedeffectivnes %in% c("Neither effective nor ineffective"), " neutral", NA)))
responses_df$think_effec <- factor(responses_df$think_effec, levels=c(" effective", " neutral", " not effective")) ##note spaces

responses_df$trust_govt <- ifelse(responses_df$Q36 %in% c("Strongly distrust", "Distrust"), "  low",
                                  ifelse(responses_df$Q36 %in% c("Strongly trust", "Somewhat trust"), "  high",
                                         ifelse(responses_df$Q36 %in% c("Neither trust nor distrust"), "  neutral", NA)))
responses_df$trust_govt <- factor(responses_df$trust_govt, levels=c("  high", "  neutral", "  low")) ##note spaces

responses_df$factu_govt <- ifelse(responses_df$Q37 %in% c("Very untruthful", "Somewhat untruthful"), "untruthful", 
                                  ifelse(responses_df$Q37 %in% c("Very truthful", "Somewhat truthful"), "truthful",
                                         ifelse(responses_df$Q37 %in% c("Very truthful", "Neither truthful nor untruthful"), "neutral", NA)))
responses_df$factu_govt <- factor(responses_df$factu_govt, levels=c("truthful", "neutral", "untruthful"))


## explore

table(responses_df$age_group, responses_df$health)
#       Poor Fair Good Excellent    
# 18-29    6   64  312       233   0
# 30-39   11  101  384       236   0
# 40-49   16  167  508       156   0
# 50-59   22  157  373        87   0
# 60+     17  155  237        31   0

## save
write.csv(responses_df,
    "clean_data.csv"
)
