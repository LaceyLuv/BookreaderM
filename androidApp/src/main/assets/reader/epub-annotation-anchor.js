// Engine-owned script, executed only through Readium's public evaluateJavascript API.
// Does not mutate the publication DOM or search another element after a mismatch.
(function(input) {
    try {
        if (!document.body || !decodeURIComponent(location.pathname).endsWith('/' + decodeURIComponent(input.href))) return null;
        function boundedText(element) {
            var walker = document.createTreeWalker(element, NodeFilter.SHOW_TEXT);
            var node, count = 0, result = '';
            while ((node = walker.nextNode())) {
                if (++count > 4096 || result.length + node.data.length > 65536) throw Error('scope limit');
                result += node.data;
            }
            return result;
        }
        function selector(element) {
            var result = [], depth = 0;
            while (element !== document.body) {
                if (!element || !element.parentElement || ++depth > 64) throw Error('invalid element');
                var index = 1, sibling = element.previousElementSibling;
                while (sibling) { if (++index > 999999) throw Error('sibling limit'); sibling = sibling.previousElementSibling; }
                result.unshift(':nth-child(' + index + ')'); element = element.parentElement;
            }
            return 'body' + (result.length ? ' > ' + result.join(' > ') : '');
        }
        function contexts(text, start, end) {
            var before = text.slice(Math.max(0, start - 256), start), after = text.slice(end, end + 256);
            if (before.length && /[\uDC00-\uDFFF]/.test(before[0])) before = before.slice(1);
            if (after.length && /[\uD800-\uDBFF]/.test(after[after.length - 1])) after = after.slice(0, -1);
            return {before:before, after:after};
        }
        function exact(text, a) {
            if (a.start < 0 || a.end <= a.start || a.end > text.length || text.slice(a.start,a.end) !== a.text) return false;
            var c = contexts(text,a.start,a.end);
            if (c.before !== a.before || c.after !== a.after) return false;
            var key = a.before + a.text + a.after, at = a.start - a.before.length;
            return text.indexOf(key) === at && text.indexOf(key,at+1) === -1;
        }
        if (input.anchor) {
            var a=input.anchor, element=document.querySelector(a.selector);
            return !!element && exact(boundedText(element),a);
        }
        var selection=window.getSelection();
        if (!selection || selection.isCollapsed || selection.rangeCount !== 1) return null;
        var range=selection.getRangeAt(0), root=range.commonAncestorContainer;
        if (root.nodeType !== Node.ELEMENT_NODE) root=root.parentElement;
        if (!document.body.contains(root)) return null;
        var full=boundedText(root), prefix=document.createRange();
        prefix.setStart(root,0); prefix.setEnd(range.startContainer,range.startOffset);
        var start=prefix.toString().length, quote=range.toString(), end=start+quote.length;
        if (!quote.trim() || quote.length>4096) return null;
        var c=contexts(full,start,end);
        var anchor={href:input.href,selector:selector(root),start:start,end:end,text:quote,before:c.before,after:c.after};
        return exact(full,anchor) ? anchor : null;
    } catch (_) { return null; }
})
