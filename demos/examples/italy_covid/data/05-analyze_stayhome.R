############################################################
# Who doesn't like staying home and why? (these are post-treatment outcomes, 
# though noting that info treatments had precisely null effects).
# 
# Author: 
# Soubhik Barari
# 
# Environment:
# - must use R 3.6
# 
# Input:
# - 00-read_clean_data.R
#
# Output:
# - fig/stayhome{.png,.pdf}
# - fig/stayhome_x_group{.png,.pdf}
############################################################

source("00-read_clean_data.R")

## get individual choices
q26_df <- tidyr::separate_rows(responses_df, "Q26", sep = ",", convert = FALSE) %>%
    filter(Q26 != "")
q26_df$Q26 <- ifelse(
    q26_df$Q26 == "Boredom", "Boredom",
    ifelse(
        q26_df$Q26 == "Combining homeschooling and working on distance", "Combining\nhomeschool\n-ing and work",
        ifelse(
            q26_df$Q26 == "Conflicts in the family", "Family conflict",
            ifelse(
                q26_df$Q26 == "Homeschooling", "Homeschool",
                ifelse(
                    q26_df$Q26 == "Lack of exercise", "Lack of\nexercise",
                    ifelse(
                        q26_df$Q26 == "Lack of freedom", "Lack of\nfreedom",
                        ifelse(
                            q26_df$Q26 == "Lack of fresh air", "Lack of\nfresh air",
                            ifelse(
                                q26_df$Q26 == "Lack of social activities", "Lack of\nsocial activity",
                                ifelse(
                                    q26_df$Q26 == "Loss of job / income source", "Loss of job\n/income source", 
                                    ifelse(
                                        q26_df$Q26 == " Loneliness", "Loneliness", NA
                                    ))))))))))

#----------------------------------------------------------
# summarise overall response
#----------------------------------------------------------

viz_barplot <- function(qcol, qname, qfn, w=7, h=5, sort=TRUE) {
    response_df <<- responses_df
    q = unlist(lapply(response_df[[qcol]], function(s) strsplit(s, ",")))
    if (sort)
        qdf = data.frame(x=q) %>% group_by(x) %>% summarise(y=n()) %>% arrange(-y) 
    else 
        qdf = data.frame(x=q) %>% group_by(x) %>% summarise(y=n())
    
    qdf$x = factor(qdf$x, levels=rev(qdf$x))
    qdf$y = (qdf$y/sum(qdf$y)) * 100
    p <- qdf %>%
        filter(!is.na(x)) %>%
        ggplot(aes(x=x, y=y)) + 
        geom_bar(stat="identity") + 
        geom_text(aes(label=sprintf("%0.1f", y), x=x, y=y+1), size=3) +
        coord_flip() + 
        xlab(qname) + ylab(sprintf("percentage of responses (n=%i)", nrow(responses_df))) +
        theme_bw()
    p
    ggsave(paste0("fig/", qfn, ".pdf"), width=w, height=h)
    ggsave(paste0("fig/", qfn, ".png"), width=w, height=h)
}

viz_barplot(qcol = "Q26", qname = "negative aspects of staying home?", qfn="stayhome", w=7, h=3)

#----------------------------------------------------------
# summarise mean responses by group
#----------------------------------------------------------

library(scales)
q26_df %>% 
    filter(gender != "Other") %>%
    filter(Q26 != "Other (specify):") %>%
    tidyr::gather("variable", "variable_value", c("gender", "health", "age_group")) %>%
    select(variable, variable_value, Q26) %>%
    mutate(variable = 
               ifelse(variable == "age_group", "age group",
                      ifelse(variable == "health", "general health",
                             ifelse(variable == "past_healthy_behavior", "past healthy\nbehavior", "gender"
                             )))) %>%
    mutate(variable_value = factor(variable_value, levels=c(
        "18-29", "30-39", "40-49", "50-59", "60+", "Poor", "Fair", "Excellent", "Male", "Female", "Other"
    ))) %>%
    mutate(Q26 = factor(Q26, levels=c( ##order by density of responses
        "Lack of\nfreedom", 
        "Boredom", 
        "Lack of\nfresh air", 
        "Lack of\nexercise", 
        "Lack of\nsocial activity", 
        "Loss of job\n/income source",
        "Loneliness",
        "Family conflict",
        "Homeschool",
        "Combining\nhomeschool\n-ing and work"
    ))) %>%
    group_by(variable, variable_value) %>%
    mutate(n_group = n()) %>%
    group_by(variable, variable_value, Q26) %>%
    summarise(
        y = (n()/head(n_group, 1))*100
    ) %>%
    filter(!is.na(variable_value), variable_value != "", !is.na(Q26)) %>%
    ggplot(aes(x=variable_value, y=y)) +
    geom_bar(stat="identity", position = position_dodge(width=0.9)) +
    geom_text(aes(label=sprintf("%0.1f", y), x=variable_value, y=y+4.9), size=3) +
    coord_flip() +
    scale_y_continuous(limits=c(0, 28),oob = rescale_none) +
    facet_grid(variable ~ Q26, scales = "free_y", space = "free_y") +
    # facet_grid(variable ~ behavior) +
    xlab("") + ylab("percentage of respondents in group that cite aspect") +
    theme_bw() +
    theme(axis.text.x = element_text(angle=45, size=8))
ggsave("fig/stayhome_x_group.pdf", height=4, width=12)
ggsave("fig/stayhome_x_group.png", height=4, width=12)

#----------------------------------------------------------
# summarise # of reasons by group
#----------------------------------------------------------

psychsoc_aspects <- c(
    " Loneliness", "Boredom", "Lack of social activity", "Lack of freedom"
)
phys_aspects <- c(
    "Lack of fresh air", "Lack of exercise"
)
workinglife <- c(
    "Homeschooling", "Loss of job / income source", "Combining homeschooling and working on distance"
)

library(scales)
responses_df$`All\naspects` <- sapply(responses_df$Q26, function(q) length(unlist(strsplit(q, split=","))))
responses_df$`Psychosocial\naspects` <- sapply(responses_df$Q26, function(q) {
    reasons <- unlist(strsplit(q, split=","))
    length(reasons[reasons %in% psychsoc_aspects])
})
responses_df$`Physical\naspects` <- sapply(responses_df$Q26, function(q) {
    reasons <- unlist(strsplit(q, split=","))
    length(reasons[reasons %in% phys_aspects])
})
responses_df$`Work\naspects` <- sapply(responses_df$Q26, function(q) {
    reasons <- unlist(strsplit(q, split=","))
    length(reasons[reasons %in% workinglife])
})

responses_df %>% 
    filter(gender != "Other") %>%
    tidyr::gather("variable", "variable_value", c("gender", "health", "age_group")) %>%
    tidyr::gather("type_aspect", "number_in_type", c("All\naspects", "Psychosocial\naspects", "Physical\naspects", "Work\naspects")) %>%
    select(variable, variable_value, type_aspect, number_in_type) %>%
    mutate(variable = 
               ifelse(variable == "age_group", "age group",
                      ifelse(variable == "health", "general health",
                             ifelse(variable == "past_healthy_behavior", "past healthy\nbehavior", "gender"
                             )))) %>%
    mutate(variable_value = factor(variable_value, levels=c(
        "18-29", "30-39", "40-49", "50-59", "60+", "Poor", "Fair", "Excellent", "Male", "Female", "Other"
    ))) %>%
    group_by(variable, variable_value, type_aspect) %>%
    summarise(
        y = mean(number_in_type, na.rm=T),
        ymin = mean(number_in_type, na.rm=T) - (1.96*sd(number_in_type, na.rm=T)/sqrt(n())),
        ymax = mean(number_in_type, na.rm=T) + (1.96*sd(number_in_type, na.rm=T)/sqrt(n()))
    ) %>%
    filter(!is.na(variable_value), variable_value != "", !is.na(y)) %>%
    ggplot(aes(x=variable_value, y=y)) +
    geom_bar(stat="identity", position = position_dodge(width=0.9)) +
    geom_errorbar(aes(ymin=ymin, ymax=ymax), position = position_dodge(width=0.9), width = 0, size=1) +
    geom_text(aes(label=sprintf("%0.1f", y), x=variable_value, y=ymax+0.5), size=3) +
    coord_flip() +
    # scale_y_continuous(limits=c(0, 28),oob = rescale_none) +
    facet_grid(variable ~ type_aspect, scales = "free_y", space = "free_y") +
    xlab("") + ylab("number of negative aspects cited") +
    theme_bw() +
    theme(axis.text.x = element_text(angle=45, size=8))

#----------------------------------------------------------
# model demographic predictors
#----------------------------------------------------------

#TODO
