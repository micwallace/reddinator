// set article from document hash
var username;
var color_vote = "#A5A5A5";
var color_upvote_active = "#FF8B60";
var color_downvote_active = "#9494FF";

function init(themeColors, user){
    username = user;
    setTheme(themeColors);
}

function setTheme(themeColors){
    var themeColors = JSON.parse(themeColors);
    $("body").css("background-color", themeColors["background_color"]);
    $("#loading_view, .reply_expand, .more_box").css("color", themeColors["load_text"]);
    $(".comment_text").css("color", themeColors["headline_text"]);
    $(".comment_user").css("color", themeColors["source_text"]);
    $(".fa-star").css("color", themeColors["votes_icon"]);
    $(".comment_score").css("color", themeColors["votes_text"]);
    $(".fa-comment").css("color", themeColors["comments_icon"]);
    $(".comment_reply_count").css("color", themeColors["comments_text"]);
    $("button").css("background-color", themeColors["header_color"]);
    $("body").show();
}

function populateFeed(json, append){
    var data = JSON.parse(json);
    $("#loading_view").hide();
    $("#base").show();
    if (append)
        $("#more").remove();
    var lastItemId = 0;
    for (var i in data){
        lastItemId = data[i].data.name;
        if (data[i].kind=="t1"){
            appendComment(data[i].data, false);
        } else if (data[i].kind=="t3") {
            appendPost(data[i].data, false);
        } else if (data[i].kind=="t4") {
            appendMessage(data[i].data, false);
        }
    }
    appendMoreButton(lastItemId);
}

function showLoadingView(text){
    var loading = $("#loading_view");
    loading.children("h4").text(text);
    $("#base").hide();
    loading.show();
}
// java bind functions
function reloadFeed(){
    showLoadingView("Loading...");
    $("#base").html('');
    Reddinator.reloadFeed($("#sort_select").val());
}

function loadFeedStart(){
    showLoadingView("Loading...");
    $("#base").html('');
}

function loadMore(moreId){
    Reddinator.loadMore(moreId);
}

function vote(thingId, direction){
    // determine if neutral vote
    if (direction == 1) {
        if ($("#"+thingId+" .upvote").css("color")=="rgb(255, 139, 96)") { // if already upvoted, neutralize.
            direction = 0;
        }
    } else { // downvote
        if ($("#"+thingId+" .downvote").css("color")=="rgb(148, 148, 255)") {
            direction = 0;
        }
    }

    Reddinator.vote(thingId, direction);
}

function voteCallback(thingId, direction){
    var upvote = $("#"+thingId).children(".vote").children(".upvote");
    var downvote = $("#"+thingId).children(".vote").children(".downvote");
    switch(direction){
        case "-1":
            upvote.css("color", color_vote);
            downvote.css("color", color_downvote_active);
            break;
        case "0":
            upvote.css("color", color_vote);
            downvote.css("color", color_vote);
            break;
        case "1":
            upvote.css("color", color_upvote_active);
            downvote.css("color", color_vote);
            break;
    }
    console.log("vote callback received: "+direction);
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

function commentCallback(parentId, commentData){
    //console.log("comment callback called");
    var postElem = $("#"+parentId+" > .post_box");
    if (commentData){
        commentData = JSON.parse(commentData);
        postElem.children("textarea").val("");
        if (parentId.indexOf("t3_")!==-1){
            $("#post_comment_button").show();
            // in case of submitting first comment
            $("#loading_view").hide();
            $("#base").show();
        }
        postElem.children('textarea').val('');
        postElem.hide();
        appendComment(commentData, true, parentId)
    }
    postElem.children("button").prop("disabled", false);
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

function startEdit(thingId){
    // skip if current is being edited
    var post_box = $("#"+thingId+" > .comment_text");
    if (!post_box.hasClass("editing")){
        // store html comment text
        post_box.data('comment_html', post_box.html());
        // prepare edit element
        var editElem = $("#edit_template").clone().show();
        editElem.find('textarea').val(post_box.text());
        // remove current html and append edit box
        post_box.html('');
        editElem.children().appendTo(post_box);
        post_box.addClass('editing');
    }
}
function cancelEdit(thingId){
    // skip if not being edited
    var post_box = $("#"+thingId+" > .comment_text");
    if (post_box.hasClass("editing")){
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
        commentData = JSON.parse(commentData);
        post_box.empty().html(htmlDecode(commentData.body_html));
        post_box.removeClass('editing');
    } else {
        post_box.children("button, textarea").prop("disabled", false);
    }
}

function noMoreCallback(moreId){
    $("#more h5").text("There's nothing more here");
}

function resetMoreClickEvent(moreId){
    var moreElem = $("#more");
    moreElem.children("h5").text('Load '+moreElem.data('rlength')+' More');
    moreElem.one('click',
        {lastItemId: moreElem.data('rname')},
        function(event){
            $(this).children("h5").text("Loading...");
            loadMore(event.data.lastItemId);
        }
    );
}

function appendMoreButton(lastItemId){
    var moreElem = $("#more_template").clone().show();
    moreElem.attr("id", "more");
    moreElem.children("h5").text("Load more");
    moreElem.data('rname', lastItemId);
    moreElem.one('click',
        {lastItemId: lastItemId},
        function(event){
            $(this).children("h5").text("Loading...");
            loadMore(event.data.lastItemId);
        }
    );
    moreElem.css("margin-right", "0").appendTo("#base");
}

function appendPost(postData, prepend){
    var postElem = $("#post_template").clone().show();
        postElem.attr("id", postData.name);
        postElem.data('url', postData.url);
        postElem.data('permalink', postData.permalink);
        postElem.data('likes', postData.likes);
        postElem.find(".post_text").html(postData.title);
        postElem.find(".post_domain").text(postData.subreddit+" - "+postData.domain);
        postElem.find(".post_score").text(postData.hide_score?'hidden':postData.score);
        postElem.find(".comment_count").text(postData.num_comments);
        // check if likes
        if (postData.hasOwnProperty('likes')){
            if (postData.likes==true){
                postElem.find(".upvote").css("color", color_upvote_active);
            } else if (postData.likes==false) {
                postElem.find(".downvote").css("color", color_downvote_active);
            }
        }
        // check thumbnail
        var thumbnail = postData.thumbnail;
        if (thumbnail && thumbnail!=""){
            if (thumbnail=="nsfw" || thumbnail=="self" || thumbnail=="default") {
                switch (thumbnail) {
                    case "nsfw":
                        thumbnail = "images/nsfw.png";
                        break;
                    case "default":
                    case "self":
                    default:
                        thumbnail = "images/self_default.png";
                        break;
                }
            }
            postElem.find(".post_thumb").attr("src", thumbnail).show();
            postElem.find(".post_text").css('margin-left', '76px');
            postElem.find(".post_main").css('min-height', '75px')
        }
        // check if author
        /*if (postData.author==username)
            postElem.find(".user_option").show();*/
        var flag = postElem.find(".distinguish_flag");
        if (postData.author==username){
            flag.text("[S]");
            flag.css("visibility", "visible");
        }
        if (postData.distinguished!=null){
            switch(postData.distinguished){
                case "moderator":
                    flag.text("[M]");
                    flag.css("color", "#30925E");
                    break;
                case "admin":
                    flag.text("[A]");
                    flag.css("color", "#F82330");
                    break;
                case "special":
                    flag.text("[Δ]");
                    flag.css("color", "#C22344");
                    break;
            }
            flag.css("visibility", "visible");
        }
        if (prepend){
            postElem.prependTo("#base");
        } else {
            postElem.appendTo("#base");
        }
}

function appendComment(commentData, prepend, parentId){
    //console.log(JSON.stringify(commentData));
    var commentElem = $("#comment_template").clone().show();
    commentElem.attr("id", commentData.name);
    var text = htmlDecode(commentData.body_html.replace(/\n\n/g, "\n").replace("\n&lt;/div&gt;", "&lt;/div&gt;")); // clean up extra line breaks
    commentElem.find(".comment_text").html(text);
    commentElem.find(".comment_user").text('/u/'+commentData.author).attr('href', 'https://www.reddit.com/u/'+commentData.author);
    commentElem.find(".comment_score").text(commentData.score_hidden?'hidden':commentData.score);
    commentElem.find(".comment_reply_count").text("0");
    // check if likes
    if (commentData.hasOwnProperty('likes')){
        if (commentData.likes==true){
            commentElem.find(".upvote").css("color", color_upvote_active);
        } else if (commentData.likes==false) {
            commentElem.find(".downvote").css("color", color_downvote_active);
        }
    }
    // check if author
    if (commentData.author==username)
        commentElem.find(".user_option").show();
    var flag = commentElem.find(".distinguish_flag");
    if (commentData.link_author==username){
        flag.text("[S]");
        flag.css("visibility", "visible");
    }
    if (commentData.distinguished!=null){
        switch(commentData.distinguished){
            case "moderator":
                flag.text("[M]");
                flag.css("color", "#30925E");
                break;
            case "admin":
                flag.text("[A]");
                flag.css("color", "#F82330");
                break;
            case "special":
                flag.text("[Δ]");
                flag.css("color", "#C22344");
                break;
        }
        flag.css("visibility", "visible");
    }
    if (parentId==null){
        parentId = "#base";
    } else {
        parentId = "#"+parentId+" .comment_replies";
        $(parentId).show();
    }
    if (prepend){
        commentElem.prependTo(parentId);
    } else {
        commentElem.appendTo(parentId);
    }
}

function appendMessage(messageData, prepend){
    //console.log(JSON.stringify(messageData));
    var messageElem = $("#message_template").clone().show();
    messageElem.attr("id", messageData.name);
    var text = htmlDecode(messageData.body_html.replace(/\n\n/g, "\n").replace("\n&lt;/div&gt;", "&lt;/div&gt;")); // clean up extra line breaks
    messageElem.find(".message_text").html(text);
    messageElem.find(".message_user").text('/u/'+messageData.author).attr('href', 'https://www.reddit.com/u/'+messageData.author);
    // check if likes
    /*if (messageData.hasOwnProperty('likes')){
        if (messageData.likes==1){
            messageElem.find(".upvote").css("color", color_upvote_active);
        } else if (messageData.likes==-1) {
            messageElem.find(".downvote").css("color", color_downvote_active);
        }
    }*/
    // check if author
    /*if (messageData.author==username)
        messageElem.find(".user_option").show();*/

    /*if (messageData.link_author==username){
        flag.text("[S]");
        flag.css("visibility", "visible");
    }*/
    var flag = messageElem.find(".distinguish_flag");
    if (messageData.distinguished!=null){
        switch(messageData.distinguished){
            case "moderator":
                flag.text("[M]");
                flag.css("color", "#30925E");
                break;
            case "admin":
                flag.text("[A]");
                flag.css("color", "#F82330");
                break;
            case "special":
                flag.text("[Δ]");
                flag.css("color", "#C22344");
                break;
        }
        flag.css("visibility", "visible");
    }
    if (prepend){
        messageElem.prependTo("#base");
    } else {
        messageElem.appendTo("#base");
    }
}

function htmlDecode(input){
    var e = document.createElement('div');
    e.innerHTML = input;
    return e.childNodes.length === 0 ? "" : e.childNodes[0].nodeValue;
}

$(function(){
    // Layout testing code
    //$("#message_template").clone().show().attr("id", 'test').appendTo("#base");
    //$("#post_template").clone().show().attr("id", 'test').appendTo("#base");
    //$("#comment_template").clone().show().attr("id", 'test').appendTo("#base");
    //$("#comment_template").clone().show().attr("id", 'test1').appendTo("#test .comment_replies");
    $(document).on('click', ".upvote", function(e){
        vote($(this).parent().parent().attr("id"), 1);
        e.stopPropagation();
    });
    $(document).on('click', ".downvote", function(e){
        vote($(this).parent().parent().attr("id"), -1);
        e.stopPropagation();
    });
    $(document).on('click', ".post_toggle", function(){
        var elem = $(this).parent().parent().parent().children(".post_reply");
        if (elem.is(":visible")){
            elem.hide();
        } else {
            $('.message_reply, .post_reply').hide();
            elem.show();
        }
    });
    $(document).on('click', ".message_reply_toggle", function(){
            var elem = $(this).parent().parent().parent().children(".message_reply");
            if (elem.is(":visible")){
                elem.hide();
            } else {
                $('.message_reply, .post_reply').hide();
                elem.show();
            }
    });
    $(document).on('click', ".post_main", function(e){
        var elem = $(this).parent();
        Reddinator.openRedditPost(elem.attr("id"), elem.data('url'), elem.data('permalink'), elem.data('likes'));
        e.stopImPropagation();
    });
});