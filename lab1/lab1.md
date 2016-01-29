
CS433/533 Homework Assignment One

Due Feb. 1, 2016 in class or by classes V2 upload if you have a digital copy. This assignment gives you a chance to analyze communication systems.

    [P1] Try to use traceroute and other tools to find:
        A destination host on the Internet so that the route from a Zoo machine to the destination has the largest number of hops that you can find. Please list the hops. What is your strategy to find such a host?
        A destination host on the Internet so that the route from a Zoo machine to the destination traverses the largest number of ISPs. You can get full credit if it has at least 5 different ISPs, but we encourage you to try to find a longer one. What is your strategy to find such a host?

Find a domain that are geometrically far away from Yale.

utl-lnx1.puk.ac.za 24 hops South Africa, National Research Foundation

6 ISP
Yale University
State of Connecticut
Ip Allocation for Geant Network
Ubuntunet Alliance for Research and Education Networking.
Uninet
Potchefstroom University for Christian Higher Education

1  anger.net.yale.edu (128.36.232.1)  3.636 ms  3.642 ms  3.641 ms
Yale University
2  10.1.2.81 (10.1.2.81)  0.390 ms  0.421 ms  0.464 ms
3  10.1.2.113 (10.1.2.113)  0.402 ms  0.449 ms  0.597 ms
4  Level3-10G-ASR.net.yale.internal (10.1.4.40)  1.759 ms  4.145 ms  4.143 ms
5  cen-10g-yale.net.yale.internal (10.1.3.102)  2.205 ms  1.790 ms  2.553 ms
6  * * *
7  enrt064hhh-9k-te0-3-0-5.net.cen.ct.gov (67.218.83.254)  2.950 ms  2.867 ms  2.985 ms
State of Connecticut
8  198.71.46.215 (198.71.46.215)  2.419 ms  2.500 ms  2.425 ms
Michigan, Ann Arbor, Internet
9  et-5-0-0.1180.rtr.newy32aoa.net.internet2.edu (198.71.46.214)  5.207 ms  5.164 ms  5.149 ms
10  198.71.45.237 (198.71.45.237)  76.614 ms  76.550 ms  76.467 ms
11  ae2.mx1.gen.ch.geant.net (62.40.98.153)  83.117 ms  83.106 ms  83.066 ms
Switzerland, geneve, ip allocation for geant Network
12  ae1.mx1.fra.de.geant.net (62.40.98.109)  91.528 ms  91.521 ms  91.479 ms
13  ubuntunet-gw.mx1.fra.de.geant.net (62.40.125.22)  98.086 ms  98.113 ms  97.904 ms
Netherlands, Amsterdam, Ip Allocation for geant network Infrastructure
14  196.32.210.177 (196.32.210.177)  111.021 ms  112.082 ms  112.039 ms
South Africa, Western Cape, Ubuntunet Alliance for research and education networking.
15  te-1-4-2018-mtz1-pe1.ubuntunet.net (196.32.209.117)  282.660 ms  282.674 ms  282.629 ms
16  xe0-0-2-700-dur1-pe2-n.tenet.ac.za (155.232.6.86)  285.863 ms  286.988 ms  287.012 ms
Western Cape, Uninet
17  te0-12-0-2-pta1-p1-n.tenet.ac.za (155.232.6.29)  330.471 ms  323.929 ms  322.393 ms
18  te0-1-0-1-jnb2-p1-n.tenet.ac.za (155.232.6.25)  336.656 ms  323.132 ms  332.443 ms
19  t8-2-jnb2-pe1-n.tenet.ac.za (155.232.7.158)  297.720 ms  297.971 ms  298.078 ms
20  155.232.14.149 (155.232.14.149)  300.850 ms  300.373 ms  300.975 ms
Limpopo, Giyani, Uninet
21  143.160.3.49 (143.160.3.49)  301.309 ms  301.366 ms  301.320 ms
Potchefstroom, Potchefstroom University for Christian Higher Education
22  143.160.3.74 (143.160.3.74)  301.666 ms  301.813 ms  301.767 ms
23  143.160.7.242 (143.160.7.242)  301.532 ms  309.918 ms  301.574 ms
24  utl-lnx1.puk.ac.za (143.160.32.1)  367.425 ms  369.060 ms  368.584 ms



    [P2] Determine the number of external phone lines that Yale will need in order to achieve a call blocking percentage of 1%. Assume that each person at Yale makes one external phone call per day, and each such phone call lasts on average 3 minutes, with the memoryless distribution. The number of people at Yale can be found at: http://www.yale.edu/about/facts.html



    [P3] Suppose that you are designing a Web server for your startup. You have acquired a single machine with a quad-core processor. Assume that CPU is the bottleneck. You anticipate that Web requests arrive (memoryless) at a rate of 15 requests/second, and benchmarking shows that it takes a core on average 200 ms to serve a Web request. What is the average service time that each Web request experiences? If it is a dual-core processor, what happens? You need to draw the state diagram when working on this problem.

    [P4] This elementary problem explore propagation delay and transmission delay, two central concepts in data networking. Consider two hosts, A and B, connected by a single link of rate R bps. Suppose that the two hosts are separated by m meters, and suppose the propagation speed along the link is s meters/sec. Host A is to send a packet of size L bits to Host B.

        Express the propagation delay, dprop, in terms of m and s.
        Determine the transmission time of the packet, dtrans, in terms of L and R.
        Ignoring processing and queuing delays, obtain an expression for the end-to-end delay.
        Suppose Host A begins to transmit the packet at time t = 0. At time t = dtrans, where is the last bit of the packet?
        Suppose dprop is greater than dtrans. At time t = dtrans, where is the first bit of the packet?
        Suppose dprop is less than dtrans. At time t = dtrans, where is the first bit of the packet?
        Suppose s = 2.5 x 108, L = 120 bits, and R = 56 kbps. Find the distance m so that dprop equals dtrans.

    [P5] Suppose two hosts, A and B, are separated by 20,000 kilometers and are connected by a direct link of R = 2 Mbps. Suppose the propagation speed over the link is 2.5 · 108 meters/sec.
        Calculate the bandwidth-delay product, R · dprop.
        Consider sending a file of 800,000 bits from Host A to Host B. Suppose the file is sent continuously as one large message. What is the maximum number of bits that will be in the link at any given time?
        Provide an interpretation of the bandwidth-delay product.
        What is the width (in meters) of a bit in the link? Is it longer than a football field?
        Derive a general expression for the width of a bit in terms of the propagation speed s, the transmission rate R, and the length of the link m.

    [P6] In this problem, we consider sending real-time voice from Host A to Host B over a packet-switched network (VoIP). Host A converts analog voice to a digital 64 kbps bit stream on the fly. Host A then groups the bits into 56-byte packets. There is one link between Hosts A and B; its transmission rate is 2 Mbps and its propagation delay is 10 msec. As soon as Host A gathers a packet, it sends it to Host B. As soon as Host B receives an entire packet, it converts the packet’s bits to an analog signal. How much time elapses from the time a bit is created (from the original analog signal at Host A) until the bit is decoded (as part of the analog signal at Host B)?

    [P7] Suppose you would like to urgently deliver 40 terabytes data from Boston to Los Angeles. You have available a 100 Mbps dedicated link for data transfer. Would you prefer to transmit the data via this link or instead use FedEx over-night delivery? Explain.

    [P8] Consider an application that transmits data at a steady rate (for example, the sender generates an N-bit unit of data every k time units, where k is small and fixed). Also, when such an application starts, it will continue running for a relatively long period of time. Answer the following questions, briefly justifying your answer:

        Would a packet-switched network or a circuit-switched network be more appropriate for this application? Why?

        Suppose that a packet-switched network is used and the only traffic in this network comes from such applications as described above. Furthermore, assume that the sum of the application data rates is less than the capacities of each and every link. Is some form of congestion control needed? Why? 

    [P9] Suppose users share a 3 Mbps link. Also suppose each user requires 150 kbps when transmitting, but each user transmits only 10 percent of the time. (You can refer to the discussion of packet switching versus circuit switching in Section 1.3.2. of the textbook, if you want.)
        When circuit switching is used, how many users can be supported?
        For the remainder of this problem, suppose packet switching is used. You can use either our queueing analysis in class or direct binomial distribution analysis. Find the probability that a given user is transmitting.
        Suppose there are 120 users. Find the probability that at any given time, exactly n users are transmitting simultaneously.
        Find the probability that there are 21 or more users transmitting simultaneously.

    [P10] In modern packet-switched networks, including the Internet, the source host segments long, application-layer messages (for example, an image or a music file) into smaller packets and sends the packets into the network. The receiver then reassembles the packets back into the original message. We refer to this process as message segmentation. The figure below illustrates the end-to-end transport of a message with and without message segmentation. Consider a message that is 8x106 bits long that is to be sent from source to destination in the figure below. Suppose each link in the figure is 2 Mbps. Ignore propagation, queuing, and processing delays.
        Consider sending the message from source to destination without message segmentation. How long does it take to move the message from the source host to the first packet switch? Keeping in mind that each switch uses store-and-forward packet switching, what is the total time to move the message from source host to destination host?
        Now suppose that the message is segmented into 800 packets, with each packet being 10,000 bits long. How long does it take to move the first packet from source host to the first switch? When the first packet is being sent from the first switch to the second switch, the second packet is being sent from the source host to the first switch. At what time will the second packet be fully received at the first switch?
        How long does it take to move the file from source host to destination host when message segmentation is used? Compare this result with your answer in part (a) and comment.
        In addition to reducing delay, what are reasons to use message segmentation?
        Discuss the drawbacks of message segmentation.


    Figure for P10: End-end message transport: (a) without message segmentation; (b) with message segmentation.


    [P11] Consider sending a large file of F bits from Host A to Host B. There are three links (and two switches) between A and B, and the links are uncongested (that is, no queuing delays). Host A segments the file into segments of S bits each and adds 80 bits of header to each segment, forming packets of L = 80 + S bits. Each link has a transmission rate of R bps. Find the value of S that minimizes the delay of moving the file from Host A to Host B. Disregard propagation delay.

    [P12] Both Skype and Google Talk offer services that allow you to make a phone call from a PC to an ordinary phone. This means that the voice call must pass through both the Internet and through a telephone network. Discuss how this might be done.


