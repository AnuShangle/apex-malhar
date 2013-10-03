/*
 * Copyright (c) 2013 DataTorrent, Inc. ALL Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

var chartOptions = { width: 600, height: 300, legend: 'none', pointSize: 0, lineWidth : 1 };

// default params
window.pageParams = {
    publisher: null,
    advertiser: null,
    adunit: null,
    refresh: 30,
    lookback: 2,
    play: null // number of minutes to play
}

jQuery(window).load(function() {
    var queryParams = splitQuery();
    pageParams = jQuery.extend({}, pageParams, queryParams);

    if (pageParams.play) {
        window.endTime = Date.now() - pageParams.play * (60 * 1000);
    }

    initFields(pageParams);
    loadCharts();
});

function loadCharts() {
    //var url = DataUrl();
    //console.log(params);
    var parameters = {
        publisher: pageParams.publisher,
        advertiser: pageParams.advertiser,
        adunit: pageParams.adunit,
        lookbackHours: pageParams.lookback,
        endTime: window.endTime
    }

    jQuery.ajax({
        url: 'data',
        data: parameters,
        success: function(data) {
            drawCostChart(data);
            drawRevenueChart(data);
            drawClicksChart(data);
            drawImpressionsChart(data);
            drawCtrChart(data);
            drawMarginChart(data);

            if (window.endTime) {
                endTime += 60 * 1000; // increment by 1 minute
            }
            setTimeout(loadCharts, pageParams.refresh * 1000);
        }
    });
}

function initFields(params) {
    populateSelect('#publisher', 'Publisher', 50);
    populateSelect('#advertiser', 'Advertiser', 100);
    populateSelect('#adunit', 'Ad Unit', 5);

    if (params['publisher']) document.getElementById('publisher').value = params['publisher'];
    if (params['advertiser']) document.getElementById('advertiser').value = params['advertiser'];
    if (params['adunit']) document.getElementById('adunit').value = params['adunit'];

    document.getElementById('refresh').value = params['refresh'];
    document.getElementById('lookback').value = params['lookback'];
}

function drawCostChart(data) {
    drawChart(data, {
        title: 'Cost Chart',
        container: 'chart_div',
        column: 'Cost',
        fn: function(item  ) {
            return item.cost;
        }
    });
}

function drawRevenueChart(data) {
    drawChart(data, {
        title: 'Revenue Chart',
        container: 'chart1_div',
        column: 'Revenue',
        fn: function(item) {
            return item.revenue;
        }
    });
}

function drawClicksChart(data) {
    drawChart(data, {
        title: 'Clicks Chart',
        container: 'chart2_div',
        column: 'Clicks',
        fn: function(item) {
            return item.clicks;
        }
    });
}

function drawImpressionsChart(data) {
    drawChart(data, {
        title: 'Impressions Chart',
        container: 'chart3_div',
        column: 'Impressions',
        fn: function(item) {
            return item.impressions;
        }
    });
}

function drawCtrChart(data) {
    drawChart(data, {
        title: 'Ctr Chart',
        container: 'chart4_div',
        column: 'Ctr',
        fn: function(item) {
            return item.clicks / item.impressions * 100;
        }
    });
}

function drawMarginChart(data) {
    drawChart(data, {
        title: 'Margin Chart',
        container: 'chart5_div',
        column: 'Margin',
        fn: function(item) {
            return (item.cost - item.revenue) / item.revenue;
        }
    });
}

function drawChart(data, options) {
    var table = new google.visualization.DataTable();
    table.addColumn('datetime', 'Time');
    table.addColumn('number', options.column);
    table.addRows(data.length);

    var costChart = new google.visualization.ScatterChart(document.getElementById(options.container));
    var costView = new google.visualization.DataView(table);

    // Populate data table
    var fn = options.fn;
    for(var i=0; i < data.length; i++)
    {
        var item = data[i];
        table.setCell(i, 0, new Date(item.timestamp));
        table.setCell(i, 1, fn(item));
    }

    // Draw line chart
    var chartOpt = { title: options.title };
    costChart.draw(costView, jQuery.extend(chartOpt, chartOptions));
}

function populateSelect(id, value, times) {
    var select = jQuery(id);
    var html = '';
    for (var i = 0; i < times; i++) {
        html += '<option value="$i">$value $i</option>'
            .replace(/\$i/g, i)
            .replace('$value', value);
    }
    select.append(html);
}

function splitQuery(query) {
    var query = window.location.search.substring(1);
    var params = {};
    var vars = query.split('&');
    for (var i=0; i < vars.length; i++) {
        var pair = vars[i].split('=');
        if(pair.length == 2) {
            params[pair[0]] = pair[1];
        }
    }
    return params;
}