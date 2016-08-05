
function LightenDarkenColor(col,amt) {
    col = parseInt(col.substring(1, 7),16);
    return "#"+(((col & 0x0000FF) + amt) | ((((col>> 8) & 0x00FF) + amt) << 8) | (((col >> 16) + amt) << 16)).toString(16);
}

function setTheme(themeColors){
    $("body").css("background-color", themeColors["background_color"]);
    $("#loading_view, .reply_expand, .more_box").css("color", themeColors["load_text"]);
    $(".border, .sub-border").css('border-color', themeColors['comments_border']);
    $(".comment_text").css("color", themeColors["comment_text"]?themeColors["comment_text"]:themeColors["headline_text"]);
    $(".comment_user").css("color", themeColors["source_text"]);
    $(".message_type, .message_subject").css("color", themeColors["load_text"]);
    $(".fa-star").css("color", themeColors["votes_icon"]);
    $(".comment_score").css("color", themeColors["votes_text"]);
    $(".fa-comment").css("color", themeColors["comments_icon"]);
    $(".comment_reply_count").css("color", themeColors["comments_count"]);
    var alt_color;
    if (themeColors.hasOwnProperty('header_color_2')){
        alt_color = LightenDarkenColor(themeColors['header_color_2'], -25);
    } else {
        themeColors['header_color_2'] = "#5F99CF";
        themeColors["header_text_2"] = "#FFFFFF";
        alt_color = "#6AABE8";
    }
    var gradient = "linear-gradient(to bottom, "+themeColors['header_color_2']+", "+alt_color+")";
    //var alt_color2 = LightenDarkenColor(themeColors['header_color_2'], -30);
    $("button").css("background", gradient);
    $("button:active").css("background", alt_color);
    $("button").css("color", themeColors["header_text_2"]);
    $("button").css("border", "2px solid "+themeColors['comments_border']);
    $("#header").css("background", gradient);
    $("#header").css("color", themeColors["header_text_2"]);
    $("body").show();
}

function addCssFile(url){
    var link = document.createElement("link");
    link.href = url;
    link.type = "text/css";
    link.rel = "stylesheet";
    link.media = "screen,print";
    document.getElementsByTagName("head")[0].appendChild(link);
}

function htmlDecode(input){
    var e = document.createElement('div');
    e.innerHTML = input;
    return e.childNodes.length === 0 ? "" : e.childNodes[0].nodeValue;
}

function vote(thingId, direction){
    // determine if neutral vote
    var vote_elem = $("#"+thingId).children(".vote");
    if (direction == 1) {
        if (vote_elem.children(".upvote").css("color")=="rgb(255, 139, 96)") { // if already upvoted, neutralize.
            direction = 0;
        }
    } else { // downvote
        if (vote_elem.children(".downvote").css("color")=="rgb(148, 148, 255)") {
            direction = 0;
        }
    }

    Reddinator.vote(thingId, direction);
}

function voteCallback(thingId, direction){
    var upvote = $("#"+thingId).children(".vote").children(".upvote");
    var downvote = $("#"+thingId).children(".vote").children(".downvote");
    var net_vote = 1;
    switch(direction){
        case "-1":
            if (upvote.css("color")=="rgb(255, 139, 96)") { // if already upvoted net vote is -2
                net_vote = -2;
            } else {
                net_vote = -1;
            }
            upvote.css("color", color_vote);
            downvote.css("color", color_downvote_active);

            break;
        case "0":
            if (upvote.css("color")=="rgb(255, 139, 96)") { // if already upvoted net vote is -1
                net_vote = -1;
            }
            upvote.css("color", color_vote);
            downvote.css("color", color_vote);
            break;
        case "1":
            if (downvote.css("color")=="rgb(148, 148, 255)") { // if already downvoted net vote is 2
                net_vote = 2;
            }
            upvote.css("color", color_upvote_active);
            downvote.css("color", color_vote);
            break;
    }
    // increment vote count
    var vote_count = $("#"+thingId).children(".comment_info").children(".comment_scores").children(".comment_score");
    var count = parseInt(vote_count.text()) || 0;
    vote_count.text(count + net_vote);
    //console.log("vote callback received: "+direction);
}

function comment(parentId, text){
    if (text==""){
        alert("Enter some text for the comment.");
        commentCallback(parentId, false);
        return;
    }
    //console.log(parentId+" "+text);
    Reddinator.comment(parentId, text);
}

function deleteComment(thingId){
    var answer = confirm("Are you sure you want to delete this comment?");
    if (answer){
        Reddinator.delete(thingId);
    }
}

function deleteCallback(thingId){
    $("#"+thingId).remove();
}

var mdEditors = {};

function removeCommentEditor(thingId){
    if (mdEditors.hasOwnProperty(thingId)){
        mdEditors[thingId].toTextArea();
        delete mdEditors[thingId];
    }
}

function startEdit(thingId){
    // skip if current is being edited
    var post_box = $("#"+thingId+" > .comment_text");
    if (!post_box.hasClass("editing")){
        // store html comment text
        post_box.data('comment_html', post_box.html());
        // prepare edit element
        var editElem = $("#edit_template").clone().show();
        var textarea = editElem.find('textarea');
        textarea.html($("#"+thingId).data("comment_md")); // use markdown text provided in data attribute; use .html() to decode entities
        // remove current html and append edit box
        post_box.html('');
        editElem.children().appendTo(post_box);
        post_box.addClass('editing');
        $('.message_reply, .post_reply').hide(); // hide other reply boxes
        if (useMdEditor){
            mdEditors[thingId] = initialiseMarkdownEditor(textarea);
        } else {
            textarea.focus();
        }
    }
}

function cancelEdit(thingId){
    // skip if not being edited
    var post_box = $("#"+thingId+" > .comment_text");
    if (post_box.hasClass("editing")){
        removeCommentEditor(thingId);
        // remove edit box and restore html content
        post_box.empty().html(post_box.data('comment_html'));
        post_box.removeClass('editing');
    }
}

function edit(thingId, text){
    if (text==""){
        alert("Enter some text for the comment.");
        editCallback(thingId, false);
        return;
    }
    Reddinator.edit(thingId, text);
}

function editCallback(thingId, commentData){
    // skip if not being edited or result false
    var post_box = $("#"+thingId+" > .comment_text");
    if (commentData && post_box.hasClass("editing")){
        removeCommentEditor(thingId);
        commentData = JSON.parse(commentData);
        $("#"+thingId).data("comment_md", commentData.body);
        post_box.empty().html(htmlDecode(commentData.body_html.replace(/\n\n/g, "\n").replace("\n&lt;/div&gt;", "&lt;/div&gt;"))); // clean up extra line breaks
        post_box.removeClass('editing');
    } else {
        post_box.children("button, textarea").prop("disabled", false);
    }
}

var useMdEditor = false;

function initialiseMarkdownEditor(textarea){
    if (ProseMirror)
        return ProseMirror.toProseMirror(textarea[0]);
}